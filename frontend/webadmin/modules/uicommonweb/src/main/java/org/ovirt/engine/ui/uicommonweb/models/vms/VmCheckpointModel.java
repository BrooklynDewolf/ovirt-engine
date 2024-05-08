package org.ovirt.engine.ui.uicommonweb.models.vms;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.ovirt.engine.core.common.businessentities.BusinessEntitiesDefinitions;
import org.ovirt.engine.core.common.businessentities.VmCheckpoint;
import org.ovirt.engine.core.common.businessentities.comparators.DiskByDiskAliasComparator;
import org.ovirt.engine.core.common.businessentities.storage.Disk;
import org.ovirt.engine.core.common.businessentities.storage.DiskImage;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.ui.uicommonweb.UICommand;
import org.ovirt.engine.ui.uicommonweb.dataprovider.AsyncDataProvider;
import org.ovirt.engine.ui.uicommonweb.models.EntityModel;
import org.ovirt.engine.ui.uicommonweb.models.ListModel;
import org.ovirt.engine.ui.uicommonweb.validation.IValidation;
import org.ovirt.engine.ui.uicommonweb.validation.LengthValidation;
import org.ovirt.engine.ui.uicommonweb.validation.NotEmptyValidation;
import org.ovirt.engine.ui.uicommonweb.validation.SpecialAsciiI18NOrNoneValidation;
import org.ovirt.engine.ui.uicompat.PropertyChangedEventArgs;

public class VmCheckpointModel extends EntityModel<VmCheckpoint> {
    private Guid vmId;

    public Guid getVmId() {
        return vmId;
    }

    public void setVmId(Guid value) {
        if (vmId != value) {
            vmId = value;
            onPropertyChanged(new PropertyChangedEventArgs("VM")); //$NON-NLS-1$
        }
    }

    private ArrayList<DiskImage> disks;

    public ArrayList<DiskImage> getDisks() {
        return disks;
    }

    public void setDisks(ArrayList<DiskImage> value) {
        if (disks != value) {
            disks = value;
            onPropertyChanged(new PropertyChangedEventArgs("Disks")); //$NON-NLS-1$
        }
    }

    private List<DiskImage> vmDisks;

    public List<DiskImage> getVmDisks() {
        return vmDisks;
    }

    public void setVmDisks(List<DiskImage> value) {
        if (vmDisks != value) {
            vmDisks = value;
            onPropertyChanged(new PropertyChangedEventArgs("VmDisks")); //$NON-NLS-1$
        }
    }

    private EntityModel<String> privateDescription;

    public EntityModel<String> getDescription() {
        return privateDescription;
    }

    public void setDescription(EntityModel<String> value) {
        privateDescription = value;
    }

    private ListModel<DiskImage> checkpointDisks;

    public ListModel<DiskImage> getCheckpointDisks() {
        return checkpointDisks;
    }

    public void setCheckpointDisks(ListModel<DiskImage> value) {
        checkpointDisks = value;
    }

    private UICommand cancelCommand;

    public UICommand getCancelCommand() {
        return cancelCommand != null ? cancelCommand : super.getCancelCommand();
    }

    public void setCancelCommand(UICommand cancelCommand) {
        this.cancelCommand = cancelCommand;
    }

    public VmCheckpointModel() {
        setDescription(new EntityModel<String>());
        setDisks(new ArrayList<DiskImage>());
        setCheckpointDisks(new ListModel<DiskImage>());
    }

    @Override
    public void initialize() {
        super.initialize();

        startProgress();
        initMessages();
    }

    private void initMessages() {
            initVmDisks();
    }

    private void initVmDisks() {
        AsyncDataProvider.getInstance().getVmDiskList(new AsyncQuery<>(disks -> {
            updateCheckpointDisks(disks);

            VmModelHelper.sendWarningForNonExportableDisks(VmCheckpointModel.this, disks, VmModelHelper.WarningType.VM_SNAPSHOT);
            getCommands().add(getOnSaveCommand());
            getCommands().add(getCancelCommand());
            stopProgress();
        }), vmId);
    }

    private void updateCheckpointDisks(List<Disk> disks) {
        List<DiskImage> diskImages =
                disks.stream()
                        .map(d -> (DiskImage) d)
                        .sorted(new DiskByDiskAliasComparator())
                        .collect(Collectors.toList());

        getCheckpointDisks().setItems(diskImages);
    }

    public boolean validate() {
        getDescription().validateEntity(new IValidation[] { new NotEmptyValidation(),
                new SpecialAsciiI18NOrNoneValidation(),
                new LengthValidation(BusinessEntitiesDefinitions.GENERAL_MAX_SIZE) });

        return getDescription().getIsValid();
    }

    private UICommand getOnSaveCommand() {
        return UICommand.createDefaultOkUiCommand("OnSave", this); //$NON-NLS-1$
    }

}
