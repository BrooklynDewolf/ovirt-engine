package org.ovirt.engine.ui.uicommonweb.models.vms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VmCheckpoint;
import org.ovirt.engine.core.common.businessentities.storage.DiskImage;
import org.ovirt.engine.core.common.businessentities.storage.DiskStorageType;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.ui.uicommonweb.UICommand;
import org.ovirt.engine.ui.uicommonweb.dataprovider.AsyncDataProvider;
import org.ovirt.engine.ui.uicommonweb.help.HelpTag;
import org.ovirt.engine.ui.uicommonweb.models.ConfirmationModel;
import org.ovirt.engine.ui.uicommonweb.models.EntityModel;
import org.ovirt.engine.ui.uicommonweb.models.SearchableListModel;
import org.ovirt.engine.ui.uicompat.ConstantsManager;
import org.ovirt.engine.ui.uicompat.PropertyChangedEventArgs;
import org.ovirt.engine.ui.uicompat.UIConstants;
import org.ovirt.engine.ui.uicompat.UIMessages;

import com.google.gwt.i18n.client.DateTimeFormat;

public class VmCheckpointListModel extends SearchableListModel<VM, VmCheckpoint> {
    // This constant is intended to be exported to a generic UTILS class later on
    private static final String DATE_FORMAT = "yyyy-MM-dd, HH:mm"; //$NON-NLS-1$

    private static final UIMessages messages = ConstantsManager.getInstance().getMessages();
    private static final UIConstants constants = ConstantsManager.getInstance().getConstants();

    private UICommand removeCommand;

    public UICommand getRemoveCommand() {
        return removeCommand;
    }

    private void setRemoveCommand(UICommand value) {
        removeCommand = value;
    }

    private EntityModel<Boolean> canSelectCheckpoint;

    public EntityModel<Boolean> getCanSelectCheckpoint() {
        return canSelectCheckpoint;
    }

    private void setCanSelectCheckpoint(EntityModel<Boolean> value) {
        canSelectCheckpoint = value;
    }

    private Map<Guid, VmCheckpointModel> vmCheckpointMap;

    public Map<Guid, VmCheckpointModel> getVmCheckpointMap() {
        return vmCheckpointMap;
    }

    public void setVmCheckpointMap(Map<Guid, VmCheckpointModel> value) {
        vmCheckpointMap = value;
        onPropertyChanged(new PropertyChangedEventArgs("VmCheckpointMap")); //$NON-NLS-1$
    }

    private List<DiskImage> vmDisks;

    public List<DiskImage> getVmDisks() {
        return vmDisks;
    }

    public void setVmDisks(List<DiskImage> value) {
        vmDisks = value;
    }

    public VmCheckpointListModel() {
        setTitle(constants.checkpointsTitle());
        setHelpTag(HelpTag.checkpoints);
        setHashName("checkpoints"); //$NON-NLS-1$

        setRemoveCommand(new UICommand("Remove", this)); //$NON-NLS-1$

        setCanSelectCheckpoint(new EntityModel<>());
        getCanSelectCheckpoint().setEntity(true);

        setVmCheckpointMap(new HashMap<>());
        setVmDisks(new ArrayList<>());

        setComparator(Comparator.comparing(VmCheckpoint::getCreationDate).reversed());
    }

    @Override
    public void setItems(Collection<VmCheckpoint> value) {
        List<VmCheckpoint> vmCheckpoints = value != null ? new ArrayList<>(value) : new ArrayList<>();
        vmCheckpoints.forEach(checkpoint -> {
            VmCheckpointModel checkpointModel = vmCheckpointMap.computeIfAbsent(checkpoint.getId(), id -> new VmCheckpointModel());
            checkpointModel.setEntity(checkpoint);
        });

        updateItems(vmCheckpoints);
    }

    private void updateItems(List<VmCheckpoint> vmCheckpoints) {
        super.setItems(vmCheckpoints);

        // Try to select the last created snapshot (fallback to active snapshot)
        if (getSelectedItem() == null && !vmCheckpoints.isEmpty()) {
            setSelectedItem(vmCheckpoints.size() > 1 ? vmCheckpoints.get(1) : vmCheckpoints.get(0));
        }

        updateActionAvailability();
    }

    @Override
    public void setEntity(VM value) {
        super.setEntity(value);
        updateVmActiveDisks();
    }

    @Override
    protected void onEntityChanged() {
        super.onEntityChanged();

        if (getEntity() != null) {
            getSearchCommand().execute();
        }
    }

    @Override
    protected void onSelectedItemChanged() {
        super.onSelectedItemChanged();
        updateActionAvailability();
    }

    @Override
    protected void selectedItemsChanged() {
        super.selectedItemsChanged();
        updateActionAvailability();
    }

    private void remove() {
        if (getEntity() != null) {
            if (getWindow() != null) {
                return;
            }

            VmCheckpoint vmCheckpoint = getSelectedItem();
            ConfirmationModel model = new ConfirmationModel();
            setWindow(model);
            model.setTitle(constants.deleteCheckpointTitle());
            model.setHelpTag(HelpTag.delete_checkpoint);
            model.setHashName("delete_checkpoint"); //$NON-NLS-1$
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(messages.areYouSureYouWantToDeleteVmCheckpoint(DateTimeFormat.getFormat(DATE_FORMAT)
                    .format(vmCheckpoint.getCreationDate()), vmCheckpoint.getDescription()));

            model.setMessage(stringBuilder.toString());

            UICommand tempVar = UICommand.createDefaultOkUiCommand("OnRemove", this); //$NON-NLS-1$
            model.getCommands().add(tempVar);
            UICommand tempVar2 = UICommand.createCancelUiCommand("Cancel", this); //$NON-NLS-1$
            model.getCommands().add(tempVar2);
        }
    }

    private void onRemove() {
        VmCheckpoint vmCheckpoint = getSelectedItem();
        if (vmCheckpoint == null) {
            cancel();
            return;
        }

        /*if (vm != null) {
            Frontend.getInstance().runAction(ActionType.RemoveSnapshot,
                    new RemoveSnapshotParameters(snapshot.getId(), vm.getId()), null, null);
        }*/

        getCanSelectCheckpoint().setEntity(false);

        cancel();
    }

    private void updateVmActiveDisks() {
        VM vm = getEntity();
        if (vm == null) {
            return;
        }

        AsyncDataProvider.getInstance().getVmDiskList(new AsyncQuery<>(disks -> {
            setVmDisks(disks
                    .stream()
                    .filter(d -> d.getDiskStorageType() != DiskStorageType.LUN)
                    .map(d -> (DiskImage) d)
                    .collect(Collectors.toList()));
        }), vm.getId());
    }

    private void cancel() {
        setWindow(null);
    }

    public void updateActionAvailability() {
        if (getItems() == null) {
            // no need to update action availability
            return;
        }

        VmCheckpoint checkpoint = getSelectedItem();

        boolean isSelected = checkpoint != null;

        getRemoveCommand().setIsExecutionAllowed(isSelected);
    }


    @Override
    public void executeCommand(UICommand command) {
        super.executeCommand(command);

        if (command == getRemoveCommand()) {
            remove();
        } else if ("OnRemove".equals(command.getName())) { //$NON-NLS-1$
            onRemove();
        } else if ("Cancel".equals(command.getName())) { //$NON-NLS-1$
            cancel();
        }
    }

    @Override
    protected String getListName() {
        return "VmCheckpointListModel"; //$NON-NLS-1$
    }

    @Override
    protected boolean isSingleSelectionOnly() {
        // Single selection model
        return true;
    }
}
