package stest.tron.wallet.transfer;

import com.google.protobuf.ByteString;
import com.googlecode.cqengine.query.simple.In;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.iterators.FilterIterator;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.AccountPaginated;

import org.tron.api.GrpcAPI.NumberMessage;
import org.tron.api.WalletExtensionGrpc;
import org.tron.api.WalletGrpc;
import org.tron.api.WalletSolidityGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.Transaction;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethed;
import stest.tron.wallet.common.client.utils.TransactionUtils;


@Slf4j
public class WalletTestTransfer005 {

  //testng001、testng002、testng003、testng004
  private final String testKey002     =
      "FC8BF0238748587B9617EB6D15D47A66C0E07C1A1959033CF249C6532DC29FE6";
  private final String testKey003     =
      "6815B367FDDE637E53E9ADC8E69424E07724333C9A2B973CFA469975E20753FC";
  private final String testKey004     =
      "592BB6C9BB255409A6A43EFD18E6A74FECDDCCE93A40D96B70FBE334E6361E32";
  private final String notexist01     =
      "DCB620820121A866E4E25905DC37F5025BFA5420B781C69E1BC6E1D83038C88A";

  /*  //testng001、testng002、testng003、testng004
  private static final byte[] fromAddress    =
      Base58.decodeFromBase58Check("THph9K2M2nLvkianrMGswRhz5hjSA9fuH7");
  private static final byte[] toAddress      =
      Base58.decodeFromBase58Check("TV75jZpdmP2juMe1dRwGrwpV6AMU6mr1EU");*/
  private static final byte[] INVAILD_ADDRESS =
      Base58.decodeFromBase58Check("27cu1ozb4mX3m2afY68FSAqn3HmMp815d48");

  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);


  private ManagedChannel channelFull = null;
  private ManagedChannel channelSolidity = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private WalletExtensionGrpc.WalletExtensionBlockingStub blockingStubExtension = null;


  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }


  @BeforeClass
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
    blockingStubExtension = WalletExtensionGrpc.newBlockingStub(channelSolidity);

    //Create a transfer.
    Assert.assertTrue(PublicMethed.sendcoin(toAddress,1000000,fromAddress,
        testKey002,blockingStubFull));
  }

  @Test(enabled = true)
  public void testgetTransactionsFromThis() {

    ByteString addressBs = ByteString.copyFrom(fromAddress);
    Account account = Account.newBuilder().setAddress(addressBs).build();
    AccountPaginated.Builder accountPaginated = AccountPaginated.newBuilder().setAccount(account);
    accountPaginated.setOffset(1000);
    accountPaginated.setLimit(0);
    GrpcAPI.TransactionList transactionList = blockingStubExtension
        .getTransactionsFromThis(accountPaginated.build());
    Optional<GrpcAPI.TransactionList>  gettransactionsfromthis = Optional
        .ofNullable(transactionList);

    if (gettransactionsfromthis.get().getTransactionCount() == 0) {
      Assert.assertTrue(PublicMethed.sendcoin(toAddress,1000000L,fromAddress,
          testKey002,blockingStubFull));
      Assert.assertTrue(PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull,
          blockingStubSolidity));
    }

    Assert.assertTrue(gettransactionsfromthis.isPresent());
    Integer beforecount = gettransactionsfromthis.get().getTransactionCount();
    logger.info(Integer.toString(beforecount));
    for (Integer j = 0; j < beforecount; j++) {
      Assert.assertFalse(gettransactionsfromthis.get().getTransaction(j)
          .getRawData().getContractList().isEmpty());
    }
  }

  @Test(enabled = true)
  public void testgetTransactionsFromThisByInvaildAddress() {
    //Invaild address.
    ByteString addressBs = ByteString.copyFrom(INVAILD_ADDRESS);
    Account account = Account.newBuilder().setAddress(addressBs).build();
    AccountPaginated.Builder accountPaginated = AccountPaginated.newBuilder().setAccount(account);
    accountPaginated.setOffset(1000);
    accountPaginated.setLimit(0);
    GrpcAPI.TransactionList transactionList = blockingStubExtension
        .getTransactionsFromThis(accountPaginated.build());
    Optional<GrpcAPI.TransactionList>  gettransactionsfromthisByInvaildAddress = Optional
        .ofNullable(transactionList);

    Assert.assertTrue(gettransactionsfromthisByInvaildAddress.get().getTransactionCount() == 0);

    //Limit is -1
    addressBs = ByteString.copyFrom(INVAILD_ADDRESS);
    account = Account.newBuilder().setAddress(addressBs).build();
    accountPaginated = AccountPaginated.newBuilder().setAccount(account);
    accountPaginated.setOffset(1000);
    accountPaginated.setLimit(-1);
    transactionList = blockingStubExtension
        .getTransactionsFromThis(accountPaginated.build());
    gettransactionsfromthisByInvaildAddress = Optional
        .ofNullable(transactionList);

    Assert.assertTrue(gettransactionsfromthisByInvaildAddress.get().getTransactionCount() == 0);

    //offset is -1
    addressBs = ByteString.copyFrom(INVAILD_ADDRESS);
    account = Account.newBuilder().setAddress(addressBs).build();
    accountPaginated = AccountPaginated.newBuilder().setAccount(account);
    accountPaginated.setOffset(-1);
    accountPaginated.setLimit(100);
    transactionList = blockingStubExtension
        .getTransactionsFromThis(accountPaginated.build());
    gettransactionsfromthisByInvaildAddress = Optional
        .ofNullable(transactionList);

    Assert.assertTrue(gettransactionsfromthisByInvaildAddress.get().getTransactionCount() == 0);

  }


  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  public Account queryAccount(ECKey ecKey,WalletGrpc.WalletBlockingStub blockingStubFull) {
    byte[] address;
    if (ecKey == null) {
      String pubKey = loadPubKey(); //04 PubKey[128]
      if (StringUtils.isEmpty(pubKey)) {
        logger.warn("Warning: QueryAccount failed, no wallet address !!");
        return null;
      }
      byte[] pubKeyAsc = pubKey.getBytes();
      byte[] pubKeyHex = Hex.decode(pubKeyAsc);
      ecKey = ECKey.fromPublicOnly(pubKeyHex);
    }
    return grpcQueryAccount(ecKey.getAddress(), blockingStubFull);
  }

  public static String loadPubKey() {
    char[] buf = new char[0x100];
    return String.valueOf(buf, 32, 130);
  }

  public byte[] getAddress(ECKey ecKey) {
    return ecKey.getAddress();
  }

  public Account grpcQueryAccount(byte[] address, WalletGrpc.WalletBlockingStub blockingStubFull) {
    ByteString addressBs = ByteString.copyFrom(address);
    Account request = Account.newBuilder().setAddress(addressBs).build();
    return blockingStubFull.getAccount(request);
  }

  public Block getBlock(long blockNum, WalletGrpc.WalletBlockingStub blockingStubFull) {
    NumberMessage.Builder builder = NumberMessage.newBuilder();
    builder.setNum(blockNum);
    return blockingStubFull.getBlockByNum(builder.build());
  }

  private Transaction signTransaction(ECKey ecKey, Transaction transaction) {
    if (ecKey == null || ecKey.getPrivKey() == null) {
      logger.warn("Warning: Can't sign,there is no private key !!");
      return null;
    }
    transaction = TransactionUtils.setTimestamp(transaction);
    return TransactionUtils.sign(transaction, ecKey);
  }
}


