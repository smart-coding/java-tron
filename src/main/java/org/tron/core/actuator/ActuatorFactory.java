package org.tron.core.actuator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.db.Manager;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction.Contract;

@Slf4j
public class ActuatorFactory {

  public static final ActuatorFactory INSTANCE = new ActuatorFactory();

  private ActuatorFactory() {
  }

  public static ActuatorFactory getInstance() {
    return INSTANCE;
  }

  /**
   * create actuator.
   */
  public static List<Actuator> createActuator(TransactionCapsule transactionCapsule,
      Manager manager) {
    List<Actuator> actuatorList = Lists.newArrayList();
    if (null == transactionCapsule || null == transactionCapsule.getInstance()) {
      logger.info("transactionCapsule or Transaction is null");
      return actuatorList;
    }

    Preconditions.checkNotNull(manager, "manager is null");
    Protocol.Transaction.raw rawData = transactionCapsule.getInstance().getRawData();
    rawData.getContractList()
        .forEach(contract -> actuatorList.add(getActuatorByContract(contract, manager)));
    return actuatorList;
  }

  private static Actuator getActuatorByContract(Contract contract, Manager manager) {
    switch (contract.getType()) {
      case AccountUpdateContract:
        return new UpdateAccountActuator(contract.getParameter(), manager);
      case TransferContract:
        return new TransferActuator(contract.getParameter(), manager);
      case TransferAssetContract:
        return new TransferAssetActuator(contract.getParameter(), manager);
      case VoteAssetContract:
        break;
      case VoteWitnessContract:
        return new VoteWitnessActuator(contract.getParameter(), manager);
      case WitnessCreateContract:
        return new WitnessCreateActuator(contract.getParameter(), manager);
      case AccountCreateContract:
        return new CreateAccountActuator(contract.getParameter(), manager);
      case AssetIssueContract:
        return new AssetIssueActuator(contract.getParameter(), manager);
      case UnfreezeAssetContract:
        return new UnfreezeAssetActuator(contract.getParameter(), manager);
      case DeployContract:
        break;
      case WitnessUpdateContract:
        return new WitnessUpdateActuator(contract.getParameter(), manager);
      case ParticipateAssetIssueContract:
        return new ParticipateAssetIssueActuator(contract.getParameter(), manager);
      case FreezeBalanceContract:
        return new FreezeBalanceActuator(contract.getParameter(), manager);
      case UnfreezeBalanceContract:
        return new UnfreezeBalanceActuator(contract.getParameter(), manager);
      case WithdrawBalanceContract:
        return new WithdrawBalanceActuator(contract.getParameter(), manager);
      case UpdateAssetContract:
        return new UpdateAssetActuator(contract.getParameter(), manager);
      default:

    }
    return null;
  }

}
