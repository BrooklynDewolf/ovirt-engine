package org.ovirt.engine.core.bll.storage.lsm;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;

import org.ovirt.engine.core.bll.CommandActionState;
import org.ovirt.engine.core.bll.LockMessagesMatchUtil;
import org.ovirt.engine.core.bll.NonTransactiveCommandAttribute;
import org.ovirt.engine.core.bll.SerialChildCommandsExecutionCallback;
import org.ovirt.engine.core.bll.SerialChildExecutingCommand;
import org.ovirt.engine.core.bll.context.CommandContext;
import org.ovirt.engine.core.bll.job.ExecutionContext;
import org.ovirt.engine.core.bll.job.ExecutionHandler;
import org.ovirt.engine.core.bll.storage.disk.MoveOrCopyDiskCommand;
import org.ovirt.engine.core.bll.storage.disk.image.ImagesHandler;
import org.ovirt.engine.core.bll.tasks.CommandHelper;
import org.ovirt.engine.core.bll.tasks.interfaces.CommandCallback;
import org.ovirt.engine.core.bll.utils.PermissionSubject;
import org.ovirt.engine.core.bll.validator.storage.StorageDomainValidator;
import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.common.VdcObjectType;
import org.ovirt.engine.core.common.action.ActionParametersBase.EndProcedure;
import org.ovirt.engine.core.common.action.ActionReturnValue;
import org.ovirt.engine.core.common.action.ActionType;
import org.ovirt.engine.core.common.action.CreateAllSnapshotsFromVmParameters;
import org.ovirt.engine.core.common.action.CreateImagePlaceholderCommandParameters;
import org.ovirt.engine.core.common.action.LiveMigrateDiskParameters;
import org.ovirt.engine.core.common.action.LiveMigrateDiskParameters.LiveDiskMigrateStage;
import org.ovirt.engine.core.common.action.LockProperties;
import org.ovirt.engine.core.common.action.LockProperties.Scope;
import org.ovirt.engine.core.common.action.RemoveSnapshotParameters;
import org.ovirt.engine.core.common.action.SyncImageGroupDataCommandParameters;
import org.ovirt.engine.core.common.businessentities.ActionGroup;
import org.ovirt.engine.core.common.businessentities.Snapshot;
import org.ovirt.engine.core.common.businessentities.StorageDomain;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.storage.Disk;
import org.ovirt.engine.core.common.businessentities.storage.DiskImage;
import org.ovirt.engine.core.common.businessentities.storage.DiskImageDynamic;
import org.ovirt.engine.core.common.businessentities.storage.DiskStorageType;
import org.ovirt.engine.core.common.businessentities.storage.ImageStatus;
import org.ovirt.engine.core.common.businessentities.storage.ImageStorageDomainMap;
import org.ovirt.engine.core.common.businessentities.storage.ImageStorageDomainMapId;
import org.ovirt.engine.core.common.businessentities.storage.StorageType;
import org.ovirt.engine.core.common.constants.StorageConstants;
import org.ovirt.engine.core.common.errors.EngineError;
import org.ovirt.engine.core.common.errors.EngineException;
import org.ovirt.engine.core.common.errors.EngineMessage;
import org.ovirt.engine.core.common.job.Step;
import org.ovirt.engine.core.common.job.StepEnum;
import org.ovirt.engine.core.common.locks.LockingGroup;
import org.ovirt.engine.core.common.utils.Pair;
import org.ovirt.engine.core.common.vdscommands.GetImageInfoVDSCommandParameters;
import org.ovirt.engine.core.common.vdscommands.VDSCommandType;
import org.ovirt.engine.core.common.vdscommands.VDSReturnValue;
import org.ovirt.engine.core.common.vdscommands.VmReplicateDiskParameters;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.compat.TransactionScopeOption;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogDirector;
import org.ovirt.engine.core.dal.job.ExecutionMessageDirector;
import org.ovirt.engine.core.dao.DiskDao;
import org.ovirt.engine.core.dao.DiskImageDao;
import org.ovirt.engine.core.dao.DiskImageDynamicDao;
import org.ovirt.engine.core.dao.ImageDao;
import org.ovirt.engine.core.dao.ImageStorageDomainMapDao;
import org.ovirt.engine.core.dao.StorageDomainDao;
import org.ovirt.engine.core.dao.VmDao;
import org.ovirt.engine.core.utils.lock.EngineLock;
import org.ovirt.engine.core.utils.transaction.TransactionSupport;
import org.ovirt.engine.core.vdsbroker.ResourceManager;
import org.ovirt.engine.core.vdsbroker.builder.vminfo.VmInfoBuildUtils;

@NonTransactiveCommandAttribute(forceCompensation = true)
public class LiveMigrateDiskCommand<T extends LiveMigrateDiskParameters> extends MoveOrCopyDiskCommand<T>implements SerialChildExecutingCommand {

    private Guid sourceQuotaId;
    private Guid sourceDiskProfileId;

    @Inject
    private ImagesHandler imagesHandler;

    @Inject
    private AuditLogDirector auditLogDirector;

    @Inject
    private LiveStorageMigrationHelper liveStorageMigrationHelper;
    @Inject
    private ResourceManager resourceManager;
    @Inject
    private ImageDao imageDao;
    @Inject
    private ImageStorageDomainMapDao imageStorageDomainMapDao;
    @Inject
    private DiskImageDynamicDao diskImageDynamicDao;
    @Inject
    private DiskImageDao diskImageDao;
    @Inject
    private VmDao vmDao;
    @Inject
    private VmInfoBuildUtils vmInfoBuildUtils;
    @Inject
    private DiskDao diskDao;
    @Inject
    private StorageDomainDao storageDomainDao;
    @Inject
    @Typed(SerialChildCommandsExecutionCallback.class)
    private Instance<SerialChildCommandsExecutionCallback> callbackProvider;

    private EngineLock liveStorageMigrationEngineLock;

    private Map<Guid, DiskImage> diskImagesMap = new HashMap<>();
    private Map<Guid, StorageDomain> storageDomainsMap = new HashMap<>();

    public LiveMigrateDiskCommand(Guid commandId) {
        super(commandId);
    }

    public LiveMigrateDiskCommand(T parameters, CommandContext commandContext) {
        super(parameters, commandContext);
    }

    @Override
    public void init() {
        super.init();
        setStoragePoolId(getVm().getStoragePoolId());
        getParameters().setStoragePoolId(getStoragePoolId());

        getParameters().setVdsId(getVdsId());
        getParameters().setDiskAlias(getDiskAlias());
        getParameters().setImageGroupID(getImageGroupId());
        getParameters().setCommandType(getActionType());

        getParameters().setDestinationImageId(((DiskImage)getDiskImageByDiskId(getParameters().getImageGroupID()))
                .getImageId());
    }

    private Disk getDiskImageByDiskId(Guid diskId) {
        Disk disk = diskDao.get(diskId);
        if (disk != null && disk.getDiskStorageType() == DiskStorageType.IMAGE) {
            DiskImage diskImage = (DiskImage) disk;
            if (!diskImagesMap.containsKey(diskImage.getImageId())) {
                diskImagesMap.put(diskImage.getImageId(), (DiskImage) disk);
            }
        }
        return disk;
    }

    @Override
    public List<PermissionSubject> getPermissionCheckSubjects() {
        DiskImage diskImage = diskImageDao.get(getParameters().getImageId());
        return Collections.singletonList(new PermissionSubject(diskImage.getId(),
                VdcObjectType.Disk,
                ActionGroup.DISK_LIVE_STORAGE_MIGRATION));
    }

    private CreateImagePlaceholderCommandParameters buildCreateImagePlacerholderParams() {
        CreateImagePlaceholderCommandParameters p = new CreateImagePlaceholderCommandParameters(
                getParameters().getStoragePoolId(),
                getParameters().getImageGroupID(),
                getParameters().getSourceStorageDomainId(),
                getParameters().getTargetStorageDomainId());
        p.setParentCommand(getActionType());
        p.setParentParameters(getParameters());
        p.setEndProcedure(EndProcedure.COMMAND_MANAGED);
        return p;
    }

    public CommandCallback getCallback() {
        return callbackProvider.get();
    }

    @Override
    protected void executeCommand() {
        imagesHandler.updateAllDiskImagesSnapshotsStatusInTransactionWithCompensation(getImageGroupIds(),
                ImageStatus.LOCKED,
                ImageStatus.OK,
                getCompensationContext());
        ActionReturnValue actionReturnValueurnValue = runInternalAction(ActionType.CreateAllSnapshotsFromVm,
                getCreateSnapshotParameters(),
                ExecutionHandler.createInternalJobContext(getContext(), getLock()));
        getParameters().setAutoGeneratedSnapshotId(actionReturnValueurnValue.getActionReturnValue());
        persistCommand(getParameters().getParentCommand(), getCallback() != null);
        setSucceeded(true);
    }

    protected CreateAllSnapshotsFromVmParameters getCreateSnapshotParameters() {
        CreateAllSnapshotsFromVmParameters params = new CreateAllSnapshotsFromVmParameters
                (getParameters().getVmId(), getDiskAlias() + " " + StorageConstants.LSM_AUTO_GENERATED_SNAPSHOT_DESCRIPTION, false);

        params.setParentCommand(ActionType.LiveMigrateDisk);
        params.setSnapshotType(Snapshot.SnapshotType.REGULAR);
        params.setParentParameters(getParameters());
        params.setImagesParameters(getParameters().getImagesParameters());
        params.setTaskGroupSuccess(getParameters().getTaskGroupSuccess());
        params.setDiskIds(getImageGroupIds());
        params.setDiskIdsToIgnoreInChecks(getImageGroupIds());
        params.setNeedsLocking(false);
        params.setEndProcedure(EndProcedure.COMMAND_MANAGED);
        return params;
    }

    private Set<Guid> getImageGroupIds() {
        return Collections.singleton(getImageGroupId());
    }


    @Override
    public boolean performNextOperation(int completedChildCount) {
        if (getParameters().getLiveDiskMigrateStage() == LiveDiskMigrateStage.CREATE_SNAPSHOT) {
            updateStage(LiveDiskMigrateStage.IMAGE_PLACEHOLDER_CREATION);
            runInternalAction(ActionType.CreateImagePlaceholder,
                    buildCreateImagePlacerholderParams(), createStepsContext(StepEnum.CLONE_IMAGE_STRUCTURE));
            return true;
        }

        if (getParameters().getLiveDiskMigrateStage() == LiveDiskMigrateStage.IMAGE_PLACEHOLDER_CREATION) {
            updateStage(LiveDiskMigrateStage.VM_REPLICATE_DISK_START);
            replicateDiskStart();
            updateStage(LiveDiskMigrateStage.IMAGE_DATA_SYNC_EXEC_START);
            syncImageData();
            updateStage(LiveDiskMigrateStage.IMAGE_DATA_SYNC_EXEC_END);
            return true;
        }

        if (getParameters().getLiveDiskMigrateStage() == LiveDiskMigrateStage.IMAGE_DATA_SYNC_EXEC_END) {
            updateStage(LiveDiskMigrateStage.VM_REPLICATE_DISK_FINISH);
            completeLiveMigration();
            updateStage(LiveDiskMigrateStage.SOURCE_IMAGE_DELETION);
            liveStorageMigrationHelper.removeImage(this, getParameters().getSourceStorageDomainId(), getParameters()
                    .getImageGroupID(), getParameters().getDestinationImageId(), AuditLogType
                    .USER_MOVE_IMAGE_GROUP_FAILED_TO_DELETE_SRC_IMAGE);
            updateStage(LiveDiskMigrateStage.LIVE_MIGRATE_DISK_EXEC_COMPLETED);
            return true;
        }

        if (isRemoveAutoGeneratedSnapshotRequired()) {
            // This lock is required to prevent CreateAllSnapshotsFromVmCommand from
            // running at the same time as RemoveSnapshotCommand
            if (!lockVm(getVmId())) {
                log.info("Failed to acquire VM lock, will retry on the next polling cycle");
                return true;
            }

            updateStage(LiveDiskMigrateStage.AUTO_GENERATED_SNAPSHOT_REMOVE_START);
            removeAutogeneratedSnapshot();
            updateStage(LiveDiskMigrateStage.AUTO_GENERATED_SNAPSHOT_REMOVE_END);

            return true;
        }

        return false;
    }

    private boolean isRemoveAutoGeneratedSnapshotRequired() {
        boolean removeSnapshotRequired = getParameters().getLiveDiskMigrateStage() == LiveDiskMigrateStage.LIVE_MIGRATE_DISK_EXEC_COMPLETED &&
                getParameters().getLiveDiskMigrateStage() != LiveDiskMigrateStage.AUTO_GENERATED_SNAPSHOT_REMOVE_END;
        if (removeSnapshotRequired) {
            if (!getVm().getStatus().isQualifiedForLiveSnapshotMerge()) {
                // If the VM is not qualified for live merge, i.e. its status is not up, the auto-generated snapshot
                // is not removed. Removing the snapshot while the VM isn't running will end up with cold merge
                // and this is not desired here.
                // Once cold merge enhanced to use qemu-img commit, this limit can be removed. See BZ 1246114.
                // This behavior can be tracked by BZ 1369942.
                log.warn("Auto-generated snapshot cannot be removed because VM isn't qualified for live merge. VM status is '{}'",
                        getVm().getStatus());
                removeSnapshotRequired = false;
            }
        }

        return removeSnapshotRequired;
    }

    private void removeAutogeneratedSnapshot() {
        RemoveSnapshotParameters removeSnapshotParameters = new RemoveSnapshotParameters(getParameters().getAutoGeneratedSnapshotId(),
                getVmId());
        removeSnapshotParameters.setEndProcedure(EndProcedure.COMMAND_MANAGED);
        removeSnapshotParameters.setParentCommand(getActionType());
        removeSnapshotParameters.setParentParameters(getParameters());
        removeSnapshotParameters.setNeedsLocking(false);

        // This is required to prevent the freeLock() in RemoveSnapshotCommand#execute
        // from being called and prematurely freeing the lock
        removeSnapshotParameters.setFreeLockNeeded(false);

        runInternalAction(ActionType.RemoveSnapshot,
                removeSnapshotParameters,
                ExecutionHandler.createInternalJobContext(createStepsContext(StepEnum.MERGE_SNAPSHOTS), liveStorageMigrationEngineLock));
    }

    private CommandContext createStepsContext(StepEnum step) {
        Step addedStep = executionHandler.addSubStep(getExecutionContext(),
                getExecutionContext().getJob().getStep(StepEnum.EXECUTING),
                step,
                ExecutionMessageDirector.resolveStepMessage(step, Collections.emptyMap()));
        ExecutionContext ctx = new ExecutionContext();
        ctx.setStep(addedStep);
        ctx.setMonitored(true);
        CommandContext commandCtx = ExecutionHandler.createDefaultContextForTasks(getContext(), null)
                .withExecutionContext(ctx);
        return commandCtx;
    }

    private void unlockDisk() {
        imageDao.updateStatusOfImagesByImageGroupId(getParameters().getImageGroupID(), ImageStatus.OK);
    }

    private boolean isConsiderSuccessful() {
        return getParameters().getLiveDiskMigrateStage() == LiveDiskMigrateStage.AUTO_GENERATED_SNAPSHOT_REMOVE_END;
    }

    @Override
    protected void endSuccessfully() {
        super.endSuccessfully();
        updateImagesInfo();
        unlockDisk();
        setSucceeded(true);
    }

    @Override
    protected void endWithFailure() {
        // if we failed to removed the source image, we should add an audit log and consider the
        // operation as successful.
        if (isConsiderSuccessful()) {
            auditLog(this, AuditLogType.USER_MOVE_IMAGE_GROUP_FAILED_TO_DELETE_SRC_IMAGE);
            this.endSuccessfully();
            return;
        }
        super.endWithFailure();
        handleDestDisk();
        unlockDisk();
        setSucceeded(true);
    }

    private void completeLiveMigration() {
        // Update the DB before sending the command (perform rollback on failure)
        moveDiskInDB(getParameters().getSourceStorageDomainId(),
                getParameters().getTargetStorageDomainId(),
                getParameters().getQuotaId(),
                getParameters().getDiskProfileId());

        try {
            replicateDiskFinish(getParameters().getSourceStorageDomainId(),
                    getParameters().getTargetStorageDomainId());
        } catch (Exception e) {
            moveDiskInDB(getParameters().getTargetStorageDomainId(),
                    getParameters().getSourceStorageDomainId(),
                    sourceQuotaId,
                    sourceDiskProfileId);
            log.error("Failed VmReplicateDiskFinish (Disk '{}', VM '{}')",
                    getParameters().getImageGroupID(),
                    getParameters().getVmId());
            throw e;
        }
    }

    private void replicateDiskFinish(Guid srcDomain, Guid dstDomain) {
        VmReplicateDiskParameters migrationStartParams = new VmReplicateDiskParameters(getParameters().getVdsId(),
                getParameters().getVmId(),
                getParameters().getStoragePoolId(),
                srcDomain,
                dstDomain,
                getParameters().getImageGroupID(),
                getParameters().getDestinationImageId(),
                null);

        VDSReturnValue ret = resourceManager.runVdsCommand(
                VDSCommandType.VmReplicateDiskFinish, migrationStartParams);

        if (!ret.getSucceeded()) {
            throw new EngineException(ret.getVdsError().getCode(), ret.getVdsError().getMessage());
        }
    }

    private boolean isMoveDiskInDbSucceded(Guid targetStorageDomainId) {
        Guid destinationImageId = getParameters().getDestinationImageId();
        DiskImage diskImage = diskImageDao.get(destinationImageId);
        return diskImage != null && targetStorageDomainId.equals(diskImage.getStorageIds().get(0));
    }

    private void moveDiskInDB(final Guid sourceStorageDomainId,
            final Guid targetStorageDomainId,
            final Guid targetQuota,
            final Guid targetDiskProfile) {
        if (isMoveDiskInDbSucceded(targetStorageDomainId)) {
            return;
        }

        TransactionSupport.executeInScope(TransactionScopeOption.Required,
                () -> {
                    for (DiskImage di : diskImageDao.getAllSnapshotsForImageGroup(getParameters().getImageGroupID())) {
                        imageStorageDomainMapDao.remove(new ImageStorageDomainMapId(di.getImageId(),
                                sourceStorageDomainId));
                        imageStorageDomainMapDao.save(new ImageStorageDomainMap(di.getImageId(),
                                targetStorageDomainId,
                                targetQuota,
                                targetDiskProfile));
                        // since moveDiskInDB can be called to 'rollback' the entity in case of
                        // an exception, we store locally the old quota and disk profile id.
                        if (sourceQuotaId == null) {
                            sourceQuotaId = di.getQuotaId();
                        }

                        if (sourceDiskProfileId == null) {
                            sourceDiskProfileId = di.getDiskProfileId();
                        }
                    }
                    return null;
                });
    }

    private void updateImagesInfo() {
        for (DiskImage image : diskImageDao.getAllSnapshotsForImageGroup(getParameters().getImageGroupID())) {
            VDSReturnValue ret = runVdsCommand(
                    VDSCommandType.GetImageInfo,
                    new GetImageInfoVDSCommandParameters(getParameters().getStoragePoolId(),
                            getParameters().getTargetStorageDomainId(),
                            getParameters().getImageGroupID(),
                            image.getImageId()));
            DiskImage imageFromIRS = (DiskImage) ret.getReturnValue();
            setQcowCompatForSnapshot(image, imageFromIRS);
            DiskImageDynamic diskImageDynamic = diskImageDynamicDao.get(image.getImageId());

            // Update image's actual size in DB
            if (imageFromIRS != null && diskImageDynamic != null) {
                diskImageDynamic.setActualSize(imageFromIRS.getActualSizeInBytes());
                diskImageDynamicDao.update(diskImageDynamic);
            }
        }
    }

    private void syncImageData() {
        SyncImageGroupDataCommandParameters parameters =
                new SyncImageGroupDataCommandParameters(getParameters().getStoragePoolId(),
                        getParameters().getImageGroupID(),
                        getParameters().getSourceStorageDomainId(),
                        getParameters().getTargetStorageDomainId());
        parameters.setEndProcedure(EndProcedure.COMMAND_MANAGED);
        parameters.setParentCommand(getActionType());
        parameters.setParentParameters(getParameters());
        runInternalAction(ActionType.SyncImageGroupData, parameters, createStepsContext(StepEnum.SYNC_IMAGE_DATA));
    }

    private void replicateDiskStart() {
        if (Guid.Empty.equals(getParameters().getVdsId())) {
            throw new EngineException(EngineError.down,
                    "VM " + getParameters().getVmId() + " is not running on any VDS");
        }

        StorageType targetType = this.getStorageDomainById(getParameters().getTargetStorageDomainId(), getParameters().getStoragePoolId()).getStorageStaticData().getStorageType();
        Optional<String> diskType = vmInfoBuildUtils.getNetworkDiskType(getVm(), targetType);

        // Start disk migration
        VmReplicateDiskParameters migrationStartParams = new VmReplicateDiskParameters(getParameters().getVdsId(),
                getParameters().getVmId(),
                getParameters().getStoragePoolId(),
                getParameters().getSourceStorageDomainId(),
                getParameters().getTargetStorageDomainId(),
                getParameters().getImageGroupID(),
                getParameters().getDestinationImageId(),
                diskType.orElse(null));
        VDSReturnValue ret = resourceManager.runVdsCommand(VDSCommandType.VmReplicateDiskStart, migrationStartParams);

        if (!ret.getSucceeded()) {
            log.error("Failed VmReplicateDiskStart (Disk '{}' , VM '{}')",
                    getParameters().getImageGroupID(),
                    getParameters().getVmId());
            throw new EngineException(ret.getVdsError().getCode(), ret.getVdsError().getMessage());
        }
    }

    private void updateStage(LiveDiskMigrateStage stage) {
        getParameters().setLiveDiskMigrateStage(stage);
        persistCommand(getParameters().getParentCommand(), getCallback() != null);
    }

    @Override
    protected LockProperties applyLockProperties(LockProperties lockProperties) {
        return lockProperties.withScope(Scope.Command);
    }

    @Override
    protected boolean checkCanBeMoveInVm() {
        return validate(createDiskValidator(getDiskImage()).isDiskPluggedToAnyNonDownVm(true));
    }

    @Override
    public VM getVm() {
        VM vm = super.getVm();
        if (vm == null) {
            vm = vmDao.getVmsListForDisk(getImageGroupId(), false).get(0);
            setVm(vm);
            setVmId(vm.getId());
        }
        return vm;
    }

    @Override
    public Guid getVdsId() {
        return getVm().getRunOnVds() != null ? getVm().getRunOnVds() : Guid.Empty;
    }

    public Guid persistAsyncTaskPlaceHolder() {
        return super.persistAsyncTaskPlaceHolder(getActionType());
    }

    public Guid persistAsyncTaskPlaceHolder(String taskKey) {
        return super.persistAsyncTaskPlaceHolder(getActionType(), taskKey);
    }

    @Override
    protected boolean validate() {
        if (!super.validate()) {
            return false;
        }

        if (!getVm().isRunningAndQualifyForDisksMigration()) {
            return failValidation(EngineMessage.CANNOT_LIVE_MIGRATE_VM_SHOULD_BE_IN_PAUSED_OR_UP_STATUS);
        }

        setStoragePoolId(getVm().getStoragePoolId());

        if (!validateDestDomainsSpaceRequirements()) {
            return false;
        }

        getReturnValue().setValid(isDiskNotShareable(getParameters().getImageId())
                    && isDiskSnapshotNotPluggedToOtherVmsThatAreNotDown(getParameters().getImageId()));

        if (!getReturnValue().isValid()) {
            return false;
        }

        if (!setAndValidateDiskProfiles()) {
            return false;
        }

        return validateCreateAllSnapshotsFromVmCommand();
    }

    protected boolean validateCreateAllSnapshotsFromVmCommand() {
        ActionReturnValue returnValue = CommandHelper.validate(ActionType.CreateAllSnapshotsFromVm,
                getCreateSnapshotParameters(), getContext().clone());
        if (!returnValue.isValid()) {
            getReturnValue().setValidationMessages(returnValue.getValidationMessages());
            return false;
        }
        return true;
    }

    private StorageDomain getStorageDomainById(Guid storageDomainId, Guid storagePoolId) {
        if (storageDomainsMap.containsKey(storageDomainId)) {
            return storageDomainsMap.get(storageDomainId);
        }

        StorageDomain storageDomain = storageDomainDao.getForStoragePool(storageDomainId, storagePoolId);
        storageDomainsMap.put(storageDomainId, storageDomain);

        return storageDomain;
    }

    protected boolean validateDestDomainsSpaceRequirements() {
        Guid destDomainId = getParameters().getTargetStorageDomainId();
        DiskImage diskImage = getDiskImageByImageId(getParameters().getImageId());
        Guid storagePoolId = diskImage.getStoragePoolId();
        StorageDomain destDomain = getStorageDomainById(destDomainId, storagePoolId);

        if (!isStorageDomainWithinThresholds(destDomain)) {
            return false;
        }

        List<DiskImage> allImageSnapshots = diskImageDao.getAllSnapshotsForLeaf(diskImage.getImageId());
        diskImage.getSnapshots().addAll(allImageSnapshots);

        StorageDomainValidator storageDomainValidator = createStorageDomainValidator(destDomain);
        if (!validate(storageDomainValidator.hasSpaceForClonedDisks(Collections.singleton(diskImage)))) {
            return false;
        }

        return true;
    }

    protected boolean isStorageDomainWithinThresholds(StorageDomain storageDomain) {
        return validate(new StorageDomainValidator(storageDomain).isDomainWithinThresholds());
    }

    private DiskImage getDiskImageByImageId(Guid imageId) {
        if (diskImagesMap.containsKey(imageId)) {
            return diskImagesMap.get(imageId);
        }

        DiskImage diskImage = diskImageDao.get(imageId);
        diskImagesMap.put(imageId, diskImage);

        return diskImage;
    }

    private boolean isDiskNotShareable(Guid imageId) {
        DiskImage diskImage = getDiskImageByImageId(imageId);

        if (diskImage.isShareable()) {
            addValidationMessageVariable("diskAliases", diskImage.getDiskAlias());
            return failValidation(EngineMessage.ACTION_TYPE_FAILED_SHAREABLE_DISK_NOT_SUPPORTED);
        }

        return true;
    }

    protected boolean isDiskSnapshotNotPluggedToOtherVmsThatAreNotDown(Guid imageId) {
        return validate(createDiskValidator(getDiskImageByImageId(imageId)).isDiskPluggedToAnyNonDownVm(true));
    }

    protected StorageDomainValidator createStorageDomainValidator(StorageDomain storageDomain) {
        return new StorageDomainValidator(storageDomain);
    }

    @Override
    protected boolean isImageNotLocked() {
        // During LSM the disks are being locked prior to the snapshot phase
        // therefore returning true here.
        return true;
    }

    @Override
    public AuditLogType getAuditLogTypeValue() {
        if (getActionState() == CommandActionState.EXECUTE) {
            if (!getParameters().getTaskGroupSuccess()) {
                return AuditLogType.USER_MOVED_DISK_FINISHED_FAILURE;
            }
            if (getSucceeded()) {
                return AuditLogType.USER_MOVED_DISK;
            }
        } else if (getActionState() == CommandActionState.END_SUCCESS || isConsiderSuccessful()) {
            return AuditLogType.USER_MOVED_DISK_FINISHED_SUCCESS;
        } else {
            return AuditLogType.USER_MOVED_DISK_FINISHED_FAILURE;
        }

        return AuditLogType.UNASSIGNED;
    }

    @Override
    protected Map<String, Pair<String, String>> getExclusiveLocks() {
        return Collections.singletonMap(getParameters().getImageGroupID().toString(),
                LockMessagesMatchUtil.makeLockingPair(LockingGroup.DISK,
                        getDiskIsBeingMigratedMessage(getDiskImageByDiskId(getParameters().getImageGroupID()))));
    }

    @Override
    protected Map<String, Pair<String, String>> getSharedLocks() {
        return Collections.singletonMap(getVmId().toString(),
                LockMessagesMatchUtil.makeLockingPair(LockingGroup.VM, EngineMessage.ACTION_TYPE_FAILED_OBJECT_LOCKED));
    }

    private String getDiskIsBeingMigratedMessage(Disk disk) {
        return EngineMessage.ACTION_TYPE_FAILED_DISK_IS_BEING_MIGRATED.name()
                + String.format("$DiskName %1$s", disk != null ? disk.getDiskAlias() : "");
    }

    private void handleDestDisk() {
        if (getParameters().getLiveDiskMigrateStage() != LiveDiskMigrateStage.IMAGE_PLACEHOLDER_CREATION &&
                getParameters().getLiveDiskMigrateStage() != LiveDiskMigrateStage.SOURCE_IMAGE_DELETION) {
            if (Guid.Empty.equals(getParameters().getVdsId())) {
                log.error("Failed during live storage migration of disk '{}' of vm '{}', as the vm is not running" +
                                " on any host not attempting to end the replication before the target disk deletion",
                        getParameters().getImageGroupID(), getParameters().getVmId());
            } else {
                log.error("Failed during live storage migration of disk '{}' of vm '{}', attempting to end " +
                        "replication before deleting the target disk",
                        getParameters().getImageGroupID(), getParameters().getVmId());
                try {
                    replicateDiskFinish(getParameters().getSourceStorageDomainId(),
                            getParameters().getSourceStorageDomainId());
                } catch (Exception e) {
                    log.error("Replication end of disk '{}' in vm '{}' back to the source failed, skipping deletion of " +
                            "the target disk", getParameters().getImageGroupID(), getParameters().getVmId());
                    return;
                }
            }

            log.error("Attempting to delete the target of disk '{}' of vm '{}'",
                    getParameters().getImageGroupID(), getParameters().getVmId());
            liveStorageMigrationHelper.removeImage(this, getParameters().getTargetStorageDomainId(), getParameters()
                    .getImageGroupID(), getParameters().getDestinationImageId(), AuditLogType
                    .USER_MOVE_IMAGE_GROUP_FAILED_TO_DELETE_DST_IMAGE);
        }
    }

    protected boolean lockVm(Guid vmId) {
        liveStorageMigrationEngineLock = new EngineLock();
        liveStorageMigrationEngineLock.setExclusiveLocks(Collections.singletonMap(vmId.toString(),
                LockMessagesMatchUtil.makeLockingPair(LockingGroup.LIVE_STORAGE_MIGRATION,
                        EngineMessage.ACTION_TYPE_FAILED_OBJECT_LOCKED)));
        Pair<Boolean, Set<String>> pair = lockManager.acquireLock(liveStorageMigrationEngineLock);
        return pair.getFirst();
    }



    @Override
    public Map<String, String> getJobMessageProperties() {
        if (jobProperties == null) {
            jobProperties = super.getJobMessageProperties();
            jobProperties.put(VdcObjectType.VM.name().toLowerCase(), getVmName());
        }
        return jobProperties;
    }
}
