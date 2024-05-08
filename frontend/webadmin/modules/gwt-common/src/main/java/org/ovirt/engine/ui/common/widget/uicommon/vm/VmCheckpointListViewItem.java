package org.ovirt.engine.ui.common.widget.uicommon.vm;

import java.util.List;

import org.gwtbootstrap3.client.ui.Column;
import org.gwtbootstrap3.client.ui.Container;
import org.gwtbootstrap3.client.ui.Row;
import org.gwtbootstrap3.client.ui.constants.ColumnSize;
import org.gwtbootstrap3.client.ui.constants.IconType;
import org.gwtbootstrap3.client.ui.html.Span;
import org.ovirt.engine.core.common.businessentities.VmCheckpoint;
import org.ovirt.engine.core.common.businessentities.VmCheckpointState;
import org.ovirt.engine.core.common.businessentities.storage.DiskImage;
import org.ovirt.engine.core.common.businessentities.storage.ImageStatus;
import org.ovirt.engine.core.common.businessentities.storage.VolumeType;
import org.ovirt.engine.core.common.utils.SizeConverter;
import org.ovirt.engine.core.compat.StringHelper;
import org.ovirt.engine.ui.common.CellTablePopupTableResources;
import org.ovirt.engine.ui.common.CommonApplicationConstants;
import org.ovirt.engine.ui.common.css.PatternflyConstants;
import org.ovirt.engine.ui.common.gin.AssetProvider;
import org.ovirt.engine.ui.common.widget.listgroup.ExpandableListViewItem;
import org.ovirt.engine.ui.common.widget.listgroup.PatternflyListViewItem;
import org.ovirt.engine.ui.common.widget.renderer.DiskSizeRenderer;
import org.ovirt.engine.ui.common.widget.renderer.FullDateTimeRenderer;
import org.ovirt.engine.ui.common.widget.table.column.AbstractTextColumn;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmCheckpointListModel;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmCheckpointModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DListElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Style;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;

public class VmCheckpointListViewItem extends PatternflyListViewItem<VmCheckpoint> {

    private static final String DL_HORIZONTAL = "dl-horizontal"; // $NON-NLS-1$

    private static final CommonApplicationConstants constants = AssetProvider.getConstants();
    private static final FullDateTimeRenderer dateRenderer = new FullDateTimeRenderer();
    private static final DiskSizeRenderer<Long> sizeRenderer = new DiskSizeRenderer<>(SizeConverter.SizeUnit.BYTES);

    private static final FullDateTimeRenderer fullDateTimeRenderer = new FullDateTimeRenderer();

    private ExpandableListViewItem generalExpand;
    private ExpandableListViewItem disksExpand;

    public VmCheckpointListViewItem(String name, VmCheckpoint checkpoint, VmCheckpointListModel listModel,
                                    VmCheckpointModel checkpointModel) {
        super(name, checkpoint);
        Container generalInfoContainer = createGeneralItemContainerPanel(checkpoint, listModel);
        generalExpand.setDetails(generalInfoContainer);
        listGroupItem.add(generalInfoContainer);
        updateValues(checkpointModel);
    }

    private Container createDisksItemContainerPanel(List<DiskImage> diskImages) {
        Row content = new Row();
        Column column = new Column(ColumnSize.MD_12);
        content.add(column);
        Container container = createItemContainerPanel(content);

        CellTable<DiskImage> disksTable = new CellTable<>(1000,
                (CellTable.Resources)GWT.create(CellTablePopupTableResources.class));

        disksTable.setWidth("98%"); // $NON-NLS-1$

        AbstractTextColumn<DiskImage> statusDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return getImageStatus(object.getImageStatus());
            }
        };
        disksTable.addColumn(statusDisk, constants.statusDisk());

        AbstractTextColumn<DiskImage> aliasDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return object.getDiskAlias();
            }
        };
        disksTable.addColumn(aliasDisk, constants.aliasDisk());

        AbstractTextColumn<DiskImage> provisionedSizeDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return String.valueOf(sizeRenderer.render(object.getSize()));
            }
        };
        disksTable.addColumn(provisionedSizeDisk, constants.provisionedSizeDisk());

        AbstractTextColumn<DiskImage> sizeDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return String.valueOf(sizeRenderer.render(object.getActualSizeInBytes()));
            }
        };
        disksTable.addColumn(sizeDisk, constants.sizeDisk());

        AbstractTextColumn<DiskImage> allocationDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return String.valueOf(VolumeType.forValue(object.getVolumeType().getValue()));
            }
        };
        disksTable.addColumn(allocationDisk, constants.allocationDisk());

        AbstractTextColumn<DiskImage> interfaceDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return getInterface(object);
            }
        };
        disksTable.addColumn(interfaceDisk, constants.interfaceDisk());

        AbstractTextColumn<DiskImage> creationDateDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return dateRenderer.render(object.getCreationDate());
            }
        };
        disksTable.addColumn(creationDateDisk, constants.creationDateDisk());

        AbstractTextColumn<DiskImage> diskSnapshotIDDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return String.valueOf(object.getImageId());
            }
        };
        disksTable.addColumn(diskSnapshotIDDisk, constants.diskSnapshotIDDisk());

        AbstractTextColumn<DiskImage> typeDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return String.valueOf(object.getDiskStorageType());
            }
        };
        disksTable.addColumn(typeDisk, constants.typeDisk());

        AbstractTextColumn<DiskImage> descriptionDisk = new AbstractTextColumn<DiskImage>() {
            @Override
            public String getValue(DiskImage object) {
                return StringHelper.isNotNullOrEmpty(object.getDiskDescription()) ? object.getDiskDescription()
                        : constants.notAvailableLabel();
            }
        };
        disksTable.addColumn(descriptionDisk, constants.descriptionDisk());

        if (!diskImages.isEmpty()) {
            column.add(disksTable);
        } else {
            column.getElement().setInnerHTML(constants.noItemsToDisplay());
        }

        disksTable.setRowData(diskImages);
        disksTable.getElement().getStyle().setMarginBottom(15, Style.Unit.PX);
        return container;
    }

    private String getInterface(DiskImage image) {
        if (image.getDiskVmElements().size() == 1) {
            return image.getDiskVmElements().iterator().next().getDiskInterface().toString();
        }
        return constants.notAvailableLabel();
    }

    private String getImageStatus(ImageStatus status) {
        switch (status) {
            case OK:
                return constants.up();
            case LOCKED:
                return constants.imageLocked();
            case ILLEGAL:
                return constants.illegalStatus();
            default:
                return constants.notAvailableLabel();
        }
    }

    private Container createGeneralItemContainerPanel(VmCheckpoint checkpoint, VmCheckpointListModel listModel) {
        Row content = new Row();
        Column column = new Column(ColumnSize.MD_12);
        content.add(column);

        DListElement dl = Document.get().createDLElement();
        dl.addClassName(DL_HORIZONTAL);
        addDetailItem(SafeHtmlUtils.fromSafeConstant(constants.dateSnapshot()),
                SafeHtmlUtils.fromString(getCreateDateString(checkpoint)), dl);
        addDetailItem(SafeHtmlUtils.fromSafeConstant(constants.statusSnapshot()),
                SafeHtmlUtils.fromString(checkpoint.getState().name()), dl);
        addDetailItem(SafeHtmlUtils.fromSafeConstant(constants.descriptionSnapshot()),
                SafeHtmlUtils.fromTrustedString(getDescription(checkpoint)), dl);

        column.getElement().appendChild(dl);
        return createItemContainerPanel(content);
    }

    private String getDescription(VmCheckpoint checkpoint) {
        String description = SafeHtmlUtils.fromString(checkpoint.getDescription()).asString();

        if (checkpoint.getState() == VmCheckpointState.CREATED) {
           description = description + " (" + constants.createdCheckpoint() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        } else if (checkpoint.getState() == VmCheckpointState.INVALID) {
            description = description + " (" + constants.invalidCheckpoint() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return description;
    }

    private String getCreateDateString(VmCheckpoint checkpoint) {
        return fullDateTimeRenderer.render(checkpoint.getCreationDate());
    }

    @Override
    public void restoreStateFromViewItem(PatternflyListViewItem<VmCheckpoint> originalViewItem) {
        VmCheckpointListViewItem original = (VmCheckpointListViewItem) originalViewItem;
        setGeneralExpanded(original.getGeneralState());
        setDisksExpanded(original.getDisksState());
    }

    @Override
    protected IsWidget createIcon() {
        Span iconSpan = new Span();
        iconSpan.addStyleName(IconType.CHECK.getCssName());
        iconSpan.addStyleName(PatternflyConstants.PF_LIST_VIEW_ICON_SM);
        iconPanel.add(iconSpan);
        return iconPanel;
    }

    @Override
    protected IsWidget createBodyPanel(SafeHtml header, VmCheckpoint entity) {
        descriptionHeaderPanel.getElement().setInnerSafeHtml(header);
        createAdditionalInfoPanel();
        return bodyPanel;
    }

    private void createAdditionalInfoPanel() {
        additionalInfoPanel.add(createGeneralAdditionalInfo());
        additionalInfoPanel.add(createDisksAdditionalInfo());
    }

    private IsWidget createGeneralAdditionalInfo() {
        FlowPanel panel = new FlowPanel();
        panel.addStyleName(PatternflyConstants.PF_LIST_VIEW_ADDITIONAL_INFO_ITEM);
        generalExpand = new ExpandableListViewItem(SafeHtmlUtils.fromString(constants.generalLabel()),
                IconType.EYE.getCssName());
        getClickHandlerRegistrations().add(generalExpand.addClickHandler(this));
        panel.add(generalExpand);
        return panel;
    }

    private IsWidget createDisksAdditionalInfo() {
        FlowPanel panel = new FlowPanel();
        panel.addStyleName(PatternflyConstants.PF_LIST_VIEW_ADDITIONAL_INFO_ITEM);
        disksExpand = new ExpandableListViewItem(SafeHtmlUtils.fromString(constants.disksLabel()),
                IconType.DATABASE.getCssName());
        getClickHandlerRegistrations().add(disksExpand.addClickHandler(this));
        panel.add(disksExpand);
        return panel;
    }

    @Override
    protected void hideAllDetails() {
        generalExpand.toggleExpanded(false);
        disksExpand.toggleExpanded(false);
    }

    @Override
    protected void toggleExpanded() {
        if (!generalExpand.isActive() && !disksExpand.isActive()) {
            removeStyleName(PatternflyConstants.PF_LIST_VIEW_EXPAND_ACTIVE);
        } else {
            addStyleName(PatternflyConstants.PF_LIST_VIEW_EXPAND_ACTIVE);
        }
    }

    @Override
    protected void toggleExpanded(boolean expand) {
        // No-op for now as we don't have an expand all option.
    }

    public boolean getGeneralState() {
        return generalExpand.isActive();
    }

    public void setGeneralExpanded(boolean value) {
        generalExpand.toggleExpanded(value);
        toggleExpanded();
    }

    public boolean getDisksState() {
        return disksExpand.isActive();
    }

    public void setDisksExpanded(boolean value) {
        disksExpand.toggleExpanded(value);
        toggleExpanded();
    }

    public void updateValues(VmCheckpointModel checkpointModel) {
        Container currentDetails = disksExpand.getDetails();
        if (currentDetails != null) {
            listGroupItem.remove(currentDetails);
        }
        Container disksInfoContainer = createDisksItemContainerPanel(checkpointModel.getDisks());
        disksExpand.setDetails(disksInfoContainer);
        disksExpand.toggleExpanded(disksExpand.isActive());
        listGroupItem.add(disksInfoContainer);

    }
}
