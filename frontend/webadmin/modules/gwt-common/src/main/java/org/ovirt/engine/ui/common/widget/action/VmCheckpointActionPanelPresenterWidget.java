package org.ovirt.engine.ui.common.widget.action;

import javax.inject.Inject;

import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VmCheckpoint;
import org.ovirt.engine.ui.common.CommonApplicationConstants;
import org.ovirt.engine.ui.common.gin.AssetProvider;
import org.ovirt.engine.ui.common.presenter.DetailActionPanelPresenterWidget;
import org.ovirt.engine.ui.common.uicommon.model.SearchableDetailModelProvider;
import org.ovirt.engine.ui.uicommonweb.UICommand;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmCheckpointListModel;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmListModel;

import com.google.web.bindery.event.shared.EventBus;

public class VmCheckpointActionPanelPresenterWidget extends
    DetailActionPanelPresenterWidget<VM, VmCheckpoint, VmListModel<Void>, VmCheckpointListModel> {

    private static final CommonApplicationConstants constants = AssetProvider.getConstants();

    @Inject
    public VmCheckpointActionPanelPresenterWidget(EventBus eventBus,
                                                  ViewDef<VM, VmCheckpoint> view,
                                                  SearchableDetailModelProvider<VmCheckpoint, VmListModel<Void>, VmCheckpointListModel> dataProvider) {
        super(eventBus, view, dataProvider);
    }

    @Override
    protected void initializeButtons() {
        addActionButton(new UiCommandButtonDefinition<VM, VmCheckpoint>(getSharedEventBus(), constants.deleteCheckpoint()) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getRemoveCommand();
            }
        });
    }

}
