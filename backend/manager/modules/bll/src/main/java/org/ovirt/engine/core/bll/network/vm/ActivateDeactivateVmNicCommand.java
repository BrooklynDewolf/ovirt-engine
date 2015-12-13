package org.ovirt.engine.core.bll.network.vm;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.core.bll.NonTransactiveCommandAttribute;
import org.ovirt.engine.core.bll.ValidationResult;
import org.ovirt.engine.core.bll.VmCommand;
import org.ovirt.engine.core.bll.context.CommandContext;
import org.ovirt.engine.core.bll.network.ExternalNetworkManager;
import org.ovirt.engine.core.bll.network.VmInterfaceManager;
import org.ovirt.engine.core.bll.network.cluster.NetworkHelper;
import org.ovirt.engine.core.bll.provider.ProviderProxyFactory;
import org.ovirt.engine.core.bll.provider.network.NetworkProviderProxy;
import org.ovirt.engine.core.bll.utils.VmDeviceUtils;
import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.common.action.ActivateDeactivateVmNicParameters;
import org.ovirt.engine.core.common.action.PlugAction;
import org.ovirt.engine.core.common.businessentities.Provider;
import org.ovirt.engine.core.common.businessentities.VMStatus;
import org.ovirt.engine.core.common.businessentities.VmDevice;
import org.ovirt.engine.core.common.businessentities.VmDeviceId;
import org.ovirt.engine.core.common.businessentities.network.Network;
import org.ovirt.engine.core.common.businessentities.network.VdsNetworkInterface;
import org.ovirt.engine.core.common.businessentities.network.VmInterfaceType;
import org.ovirt.engine.core.common.businessentities.network.VnicProfile;
import org.ovirt.engine.core.common.errors.EngineException;
import org.ovirt.engine.core.common.errors.EngineMessage;
import org.ovirt.engine.core.common.vdscommands.VmNicDeviceVDSParameters;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dao.network.InterfaceDao;
import org.ovirt.engine.core.utils.transaction.TransactionMethod;
import org.ovirt.engine.core.utils.transaction.TransactionSupport;
import org.ovirt.engine.core.vdsbroker.xmlrpc.XmlRpcStringUtils;

/**
 * Activate or deactivate a virtual network interface of a VM in case it is in a valid status. If the VM is down, simply
 * update the device, if it is Up - HotPlug / HotUnPlug the virtual network interface
 */
@NonTransactiveCommandAttribute
public class ActivateDeactivateVmNicCommand<T extends ActivateDeactivateVmNicParameters> extends VmCommand<T> {

    private VmDevice vmDevice;

    private VnicProfile vnicProfile;

    private Network network;

    private NetworkProviderProxy providerProxy;

    public ActivateDeactivateVmNicCommand(T parameters) {
        this(parameters, null);
    }

    public ActivateDeactivateVmNicCommand(T parameters, CommandContext commandContext) {
        super(parameters, commandContext);
        setVmId(parameters.getVmId());
    }

    @Override
    protected boolean canDoAction() {

        if (getVm() == null) {
            addCanDoActionMessage(EngineMessage.ACTION_TYPE_FAILED_VM_NOT_FOUND);
            return false;
        }

        if (!canRunActionOnNonManagedVm()) {
            return false;
        }

        if (!activateDeactivateVmNicAllowed(getVm().getStatus())) {
            addCanDoActionMessage(EngineMessage.ACTIVATE_DEACTIVATE_NIC_VM_STATUS_ILLEGAL);
            return false;
        }

        // HotPlug in the host needs to be called only if the Vm is UP
        if (hotPlugVmNicRequired(getVm().getStatus())) {
            setVdsId(getVm().getRunOnVds());
            if (!canPerformNicHotPlug()) {
                return false;
            }

            // External networks are handled by their provider, so only check if exists on host for internal networks.
            if (getNetwork() != null
                    && !getNetwork().isExternal()
                    && !networkAttachedToVds(getNetwork().getName(), getVdsId())) {
                addCanDoActionMessage(EngineMessage.ACTIVATE_DEACTIVATE_NETWORK_NOT_IN_VDS);
                return false;
            }

            if (failPassthroughVnicHotPlug()) {
                return false;
            }
        }

        vmDevice = getVmDeviceDao().get(new VmDeviceId(getParameters().getNic().getId(), getParameters().getVmId()));
        if (vmDevice == null) {
            addCanDoActionMessage(EngineMessage.VM_INTERFACE_NOT_EXIST);
            return false;
        }

        if (!validate(macAvailable())) {
            return false;
        }

        return true;
    }

    public Network getNetwork() {
        if (getParameters().getNic().getVnicProfileId() != null && network == null) {
            vnicProfile = getDbFacade().getVnicProfileDao().get(getParameters().getNic().getVnicProfileId());
            network = NetworkHelper.getNetworkByVnicProfile(vnicProfile);
        }

        return network;
    }

    public String getInterfaceName() {

        return getParameters().getNic().getName();
    }

    public String getInterfaceType() {

        return VmInterfaceType.forValue(getParameters().getNic().getType()).getDescription();
    }

    @Override
    protected void executeVmCommand() {
        boolean isNicToBePlugged = getParameters().getAction() == PlugAction.PLUG;
        if (isNicToBePlugged){
            clearAddressIfPciSlotIsDuplicated(vmDevice);
        }
        // HotPlug in the host is called only if the Vm is UP
        if (hotPlugVmNicRequired(getVm().getStatus())) {
            boolean externalNetworkIsPlugged = isNicToBePlugged
                    && getNetwork() != null
                    && getNetwork().isExternal();
            if (externalNetworkIsPlugged) {
                plugToExternalNetwork();
            }

            try {
                runVdsCommand(getParameters().getAction().getCommandType(),
                    new VmNicDeviceVDSParameters(getVdsId(),
                            getVm(),
                            getParameters().getNic(),
                            vmDevice));
            } catch (EngineException e) {
                if (externalNetworkIsPlugged && getParameters().isNewNic()) {
                    unplugFromExternalNetwork();
                }

                throw e;
            }
        }
        // In any case, the device is updated
        TransactionSupport.executeInNewTransaction(updateDevice());
        setSucceeded(true);
    }

    private void clearAddressIfPciSlotIsDuplicated(VmDevice vmDeviceToHotplug) {
        if (searchForDuplicatesWithExistingVmDevices(vmDeviceToHotplug)){
            vmDeviceToHotplug.setAddress("");
        }
    }

    private boolean searchForDuplicatesWithExistingVmDevices(VmDevice vmDeviceToHotplug){
        String deviceAddress = vmDeviceToHotplug.getAddress();
        if (StringUtils.isEmpty(deviceAddress)){
            return false;
        }
        Map<String, String> addressMapToHotplug = XmlRpcStringUtils.string2Map(deviceAddress);
        List<VmDevice> allVmDevices = getVmDeviceDao().getVmDeviceByVmId(getVm().getId());
        for (VmDevice vmDevice : allVmDevices) {
            if (!vmDeviceToHotplug.getId().equals(vmDevice.getId())){
                Map<String, String> deviceAddressMap = XmlRpcStringUtils.string2Map(vmDevice.getAddress());
                if(deviceAddressMap.equals(addressMapToHotplug)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void plugToExternalNetwork() {
        Map<String, String> runtimeProperties =
                getProviderProxy().allocate(getNetwork(), vnicProfile, getParameters().getNic(), getVds());

        if (runtimeProperties != null) {
            getVm().getRuntimeDeviceCustomProperties().put(vmDevice.getId(), runtimeProperties);
        }
    }

    private void unplugFromExternalNetwork() {
        new ExternalNetworkManager(getParameters().getNic(), getNetwork()).deallocateIfExternal();
    }

    private NetworkProviderProxy getProviderProxy() {
        if (providerProxy == null) {
            Provider<?> provider = getDbFacade().getProviderDao().get(getNetwork().getProvidedBy().getProviderId());
            providerProxy = ProviderProxyFactory.getInstance().create(provider);
        }

        return providerProxy;
    }

    private TransactionMethod<Void> updateDevice() {
        return new TransactionMethod<Void>() {

            @Override
            public Void runInTransaction() {
                vmDevice.setIsPlugged(getParameters().getAction() == PlugAction.PLUG ? true : false);
                getVmDeviceDao().update(vmDevice);
                VmDeviceUtils.updateBootOrder(getVm().getId());
                return null;
            }
        };
    }

    @Override
    protected void setActionMessageParameters() {
        addCanDoActionMessage((getParameters().getAction() == PlugAction.PLUG) ?
                EngineMessage.VAR__ACTION__ACTIVATE : EngineMessage.VAR__ACTION__DEACTIVATE);
        addCanDoActionMessage(EngineMessage.VAR__TYPE__INTERFACE);
    }

    @Override
    public AuditLogType getAuditLogTypeValue() {
        if (getParameters().getAction() == PlugAction.PLUG) {
            return getSucceeded() ? AuditLogType.NETWORK_ACTIVATE_VM_INTERFACE_SUCCESS
                    : AuditLogType.NETWORK_ACTIVATE_VM_INTERFACE_FAILURE;
        } else {
            return getSucceeded() ? AuditLogType.NETWORK_DEACTIVATE_VM_INTERFACE_SUCCESS
                    : AuditLogType.NETWORK_DEACTIVATE_VM_INTERFACE_FAILURE;
        }
    }

    private boolean activateDeactivateVmNicAllowed(VMStatus vmStatus) {
        return vmStatus == VMStatus.Up || vmStatus == VMStatus.Down || vmStatus == VMStatus.ImageLocked;
    }

    private boolean networkAttachedToVds(String networkName, Guid vdsId) {
        List<VdsNetworkInterface> listOfInterfaces = getInterfaceDao().getAllInterfacesForVds(vdsId);
        for (VdsNetworkInterface vdsNetworkInterface : listOfInterfaces) {
            if (networkName.equals(vdsNetworkInterface.getNetworkName())) {
                return true;
            }
        }
        return false;
    }

    protected InterfaceDao getInterfaceDao() {
        return getDbFacade().getInterfaceDao();
    }

    private boolean hotPlugVmNicRequired(VMStatus vmStatus) {
        return vmStatus == VMStatus.Up;
    }

    protected ValidationResult macAvailable() {
        Boolean allowDupMacs = getMacPool().isDuplicateMacAddressesAllowed();
        VmInterfaceManager vmInterfaceManager = new VmInterfaceManager(getMacPool());
        if (allowDupMacs || !vmInterfaceManager.existsPluggedInterfaceWithSameMac(getParameters().getNic())) {
            return ValidationResult.VALID;
        } else {
            return new ValidationResult(EngineMessage.NETWORK_MAC_ADDRESS_IN_USE);
        }
    }

    protected boolean failPassthroughVnicHotPlug() {
        if (VmInterfaceType.pciPassthrough == VmInterfaceType.forValue(getParameters().getNic().getType())) {
            addCanDoActionMessage(EngineMessage.HOT_PLUG_UNPLUG_PASSTHROUGH_VNIC_NOT_SUPPORTED);
            return true;
        }
        return false;
    }
}
