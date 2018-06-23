package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.AssetIssueCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.utils.TransactionUtil;
import org.tron.core.config.Parameter.ChainConstant;
import org.tron.core.db.AssetIssueStore;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.AccountUpdateContract;
import org.tron.protos.Contract.UpdateAssetContract;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j
public class UpdateAssetActuator extends AbstractActuator {

  UpdateAssetActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final UpdateAssetContract updateAssetContract = this.contract
          .unpack(UpdateAssetContract.class);

      long newLimit = updateAssetContract.getNewLimit();
      long newPublicLimit = updateAssetContract.getNewPublicLimit();
      byte[] ownerAddress = updateAssetContract.getOwnerAddress().toByteArray();
      ByteString newUrl = updateAssetContract.getUrl();
      ByteString newDescription = updateAssetContract.getDescription();

      AssetIssueStore assetIssueStore = dbManager.getAssetIssueStore();
      AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      AssetIssueCapsule assetIssueCapsule =
          assetIssueStore.get(accountCapsule.getAssetIssuedName().toByteArray());

      assetIssueCapsule.setFreeAssetNetLimit(newLimit);
      assetIssueCapsule.setPublicFreeAssetNetLimit(newPublicLimit);
      assetIssueCapsule.setUrl(newUrl);
      assetIssueCapsule.setDescription(newDescription);
      assetIssueStore.put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {

    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(UpdateAssetContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [UpdateAssetContract],real type[" + contract
              .getClass() + "]");
    }
    final UpdateAssetContract updateAssetContract;
    try {
      updateAssetContract = this.contract.unpack(UpdateAssetContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    long newLimit = updateAssetContract.getNewLimit();
    long newPublicLimit = updateAssetContract.getNewPublicLimit();
    byte[] ownerAddress = updateAssetContract.getOwnerAddress().toByteArray();
    ByteString newUrl = updateAssetContract.getUrl();
    ByteString newDescription = updateAssetContract.getDescription();

    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid ownerAddress");
    }

    AccountCapsule account = dbManager.getAccountStore().get(ownerAddress);
    if (account == null) {
      throw new ContractValidateException("Account has not existed");
    }

    if (account.getAssetIssuedName().isEmpty()) {
      throw new ContractValidateException("Account has not issue any asset");
    }

    assert (dbManager.getAssetIssueStore().get(account.getAssetIssuedName().toByteArray()) != null);

    if (!TransactionUtil.validUrl(newUrl.toByteArray())) {
      throw new ContractValidateException("Invalid url");
    }

    if (!TransactionUtil.validAssetDescription(newDescription.toByteArray())) {
      throw new ContractValidateException("Invalid description");
    }

    if (newLimit < 0 || newLimit >= ChainConstant.ONE_DAY_NET_LIMIT) {
      throw new ContractValidateException("Invalid FreeAssetNetLimit");
    }

    if (newPublicLimit < 0 || newPublicLimit >= ChainConstant.ONE_DAY_NET_LIMIT) {
      throw new ContractValidateException("Invalid PublicFreeAssetNetLimit");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AccountUpdateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
