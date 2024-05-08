package org.ovirt.engine.ui.webadmin.section.main.view.tab.virtualMachine;

import java.util.Map;

import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VmCheckpoint;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.ui.common.idhandler.ElementIdHandler;
import org.ovirt.engine.ui.common.presenter.AbstractSubTabPresenter;
import org.ovirt.engine.ui.common.uicommon.model.SearchableDetailModelProvider;
import org.ovirt.engine.ui.common.view.AbstractDetailTabListView;
import org.ovirt.engine.ui.common.widget.listgroup.PatternflyListView;
import org.ovirt.engine.ui.common.widget.listgroup.PatternflyListViewItem;
import org.ovirt.engine.ui.common.widget.listgroup.PatternflyListViewItemCreator;
import org.ovirt.engine.ui.common.widget.uicommon.vm.VmCheckpointListViewItem;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmCheckpointListModel;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmCheckpointModel;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmListModel;
import org.ovirt.engine.ui.webadmin.section.main.presenter.tab.virtualMachine.SubTabVirtualMachineCheckpointPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;

public class SubTabVirtualMachineCheckpointView extends AbstractDetailTabListView<VM, VmListModel<Void>,
        VmCheckpointListModel> implements SubTabVirtualMachineCheckpointPresenter.ViewDef,
        PatternflyListViewItemCreator<VmCheckpoint> {

    interface ViewIdHandler extends ElementIdHandler<SubTabVirtualMachineCheckpointView> {
        ViewIdHandler idHandler = GWT.create(ViewIdHandler.class);
    }

    private PatternflyListView<VM, VmCheckpoint, VmCheckpointListModel> checkpointListView;

    @Inject
    public SubTabVirtualMachineCheckpointView(SearchableDetailModelProvider<VmCheckpoint, VmListModel<Void>,
                VmCheckpointListModel> modelProvider) {
        super(modelProvider);
        ViewIdHandler.idHandler.generateAndSetIds(this);
        checkpointListView = new PatternflyListView<>();

        checkpointListView.setCreator(this);
        checkpointListView.setModel(modelProvider.getModel());
        getContentPanel().add(checkpointListView);
        initWidget(getContentPanel());
    }

    @Override
    public void setInSlot(Object slot, IsWidget content) {
        if (slot == AbstractSubTabPresenter.TYPE_SetActionPanel) {
            getContainer().insert(content, 0);
        } else {
            super.setInSlot(slot, content);
        }
    }

    @Override
    public void setMainSelectedItem(VM selectedItem) {
        // Not interested in current selected VM.
    }

    @Override
    public PatternflyListViewItem<VmCheckpoint> createListViewItem(VmCheckpoint selectedItem) {
        Map<Guid, VmCheckpointModel> checkpointsMap = getDetailModel().getVmCheckpointMap();
        VmCheckpointModel vmCheckpointModel = checkpointsMap.get(selectedItem.getId());
        VmCheckpointListViewItem newItem = new VmCheckpointListViewItem(selectedItem.getDescription(), selectedItem,
                getDetailModel(), vmCheckpointModel);
        // vmCheckpointModel.updateVmConfiguration(returnValue -> newItem.updateValues(vmCheckpointModel));
        return newItem;
    }

    @Override
    protected void generateIds() {
        ViewIdHandler.idHandler.generateAndSetIds(this);
    }
}
