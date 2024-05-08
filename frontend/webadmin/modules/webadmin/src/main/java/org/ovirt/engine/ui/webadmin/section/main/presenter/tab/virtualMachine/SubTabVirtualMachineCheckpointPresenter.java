package org.ovirt.engine.ui.webadmin.section.main.presenter.tab.virtualMachine;

import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VmCheckpoint;
import org.ovirt.engine.ui.common.presenter.AbstractSubTabPresenter;
import org.ovirt.engine.ui.common.uicommon.model.SearchableDetailModelProvider;
import org.ovirt.engine.ui.common.widget.action.VmCheckpointActionPanelPresenterWidget;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmCheckpointListModel;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmListModel;
import org.ovirt.engine.ui.uicommonweb.place.WebAdminApplicationPlaces;
import org.ovirt.engine.ui.webadmin.section.main.presenter.tab.DetailTabDataIndex;

import com.google.gwt.event.shared.EventBus;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.TabData;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.TabInfo;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.TabContentProxyPlace;

public class SubTabVirtualMachineCheckpointPresenter
    extends AbstractSubTabVirtualMachinePresenter<VmCheckpointListModel, SubTabVirtualMachineCheckpointPresenter.ViewDef,
        SubTabVirtualMachineCheckpointPresenter.ProxyDef> {

    @ProxyCodeSplit
    @NameToken(WebAdminApplicationPlaces.virtualMachineCheckpointSubTabPlace)
    public interface ProxyDef extends TabContentProxyPlace<SubTabVirtualMachineCheckpointPresenter> {
    }

    public interface ViewDef extends AbstractSubTabPresenter.ViewDef<VM> {
    }

    @TabInfo(container = VirtualMachineSubTabPanelPresenter.class)
    static TabData getTabData() {
        return DetailTabDataIndex.VIRTUALMACHINE_CHECKPOINT;
    }

    @Inject
    public SubTabVirtualMachineCheckpointPresenter(EventBus eventBus, ViewDef view, ProxyDef proxy,
                                                   PlaceManager placeManager, VirtualMachineMainSelectedItems selectedItems,
                                                   VmCheckpointActionPanelPresenterWidget actionPanel,
                                                   SearchableDetailModelProvider<VmCheckpoint, VmListModel<Void>, VmCheckpointListModel> modelProvider) {
        super(eventBus, view, proxy, placeManager, modelProvider, selectedItems, actionPanel,
                VirtualMachineSubTabPanelPresenter.TYPE_SetTabContent);
    }

}
