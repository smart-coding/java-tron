package stest.tron.wallet.assetissue;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.AccountNetMessage;
import org.tron.api.GrpcAPI.NumberMessage;
import org.tron.api.GrpcAPI.Return;
import org.tron.api.WalletGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.Transaction;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethed;
import stest.tron.wallet.common.client.utils.TransactionUtils;

@Slf4j
public class WalletTestAssetIssue007 {

  //testng001、testng002、testng003、testng004
  private final String testKey002 =
      "FC8BF0238748587B9617EB6D15D47A66C0E07C1A1959033CF249C6532DC29FE6";
  private final String testKey003 =
      "6815B367FDDE637E53E9ADC8E69424E07724333C9A2B973CFA469975E20753FC";

  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress   = PublicMethed.getFinalAddress(testKey003);


  private static final long now = System.currentTimeMillis();
  private static String name = "AssetIssue007_" + Long.toString(now);
  private static final long totalSupply = now;
  private static final long sendAmount = 10000000000L;
  private static final long netCostMeasure = 200L;
  private static final Integer trxNum = 2;
  private static final Integer icoNum = 1;

  Long freeAssetNetLimit = 10000L;
  Long publicFreeAssetNetLimit = 10000L;
  String description = "for case assetissue007";
  String url = "https://stest.assetissue007.url";

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  //get account
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] asset007Address = ecKey1.getAddress();
  String testKeyForAssetIssue007 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());


  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] participateAssetAddress = ecKey2.getAddress();
  String participateAssetCreateKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  @BeforeClass(enabled = true)
  public void beforeClass() {
    logger.info(testKeyForAssetIssue007);
    logger.info(participateAssetCreateKey);

    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    //Sendcoin to this account
    ByteString addressBS1 = ByteString.copyFrom(asset007Address);
    Account request1 = Account.newBuilder().setAddress(addressBS1).build();
    GrpcAPI.AssetIssueList assetIssueList1 = blockingStubFull
        .getAssetIssueByAccount(request1);
    Optional<GrpcAPI.AssetIssueList> queryAssetByAccount = Optional.ofNullable(assetIssueList1);
    if (queryAssetByAccount.get().getAssetIssueCount() == 0) {
      Assert.assertTrue(PublicMethed.freezeBalance(fromAddress, 10000000, 3, testKey002,
          blockingStubFull));
      Assert.assertTrue(PublicMethed
          .sendcoin(asset007Address, sendAmount, fromAddress, testKey002, blockingStubFull));
      Assert.assertTrue(PublicMethed
          .freezeBalance(asset007Address, 100000000L, 3, testKeyForAssetIssue007,
              blockingStubFull));
      Long start = System.currentTimeMillis() + 2000;
      Long end = System.currentTimeMillis() + 1000000000;
      Assert.assertTrue(PublicMethed
          .createAssetIssue(asset007Address, name, totalSupply, trxNum, icoNum, start, end, 1, description,
              url, freeAssetNetLimit, publicFreeAssetNetLimit, 1L, 1L, testKeyForAssetIssue007,
              blockingStubFull));
    } else {
      logger.info("This account already create an assetisue");
      Optional<GrpcAPI.AssetIssueList> queryAssetByAccount1 = Optional.ofNullable(assetIssueList1);
      name = ByteArray.toStr(queryAssetByAccount1.get().getAssetIssue(0).getName().toByteArray());
    }
  }

  @Test(enabled = false)
  public void testParticipateAssetIssueUseParticipaterBandwidth() {
    logger.info(name);
    //When no balance, participate an asset issue
    Assert.assertFalse(PublicMethed.participateAssetIssue(asset007Address, name.getBytes(),
        1L, participateAssetAddress, participateAssetCreateKey,blockingStubFull));

    ByteString addressBs = ByteString.copyFrom(asset007Address);
    Account request = Account.newBuilder().setAddress(addressBs).build();
    AccountNetMessage asset007NetMessage = blockingStubFull.getAccountNet(request);
    final Long asset007BeforeFreeNetUsed = asset007NetMessage.getFreeNetUsed();

    //SendCoin to participate account.
    Assert.assertTrue(PublicMethed.sendcoin(participateAssetAddress,10000000L,
        fromAddress,testKey002,blockingStubFull));
    addressBs = ByteString.copyFrom(participateAssetAddress);
    request = Account.newBuilder().setAddress(addressBs).build();
    AccountNetMessage participateAccountNetMessage = blockingStubFull.getAccountNet(request);
    final Long participateAccountBeforeNetUsed = participateAccountNetMessage.getFreeNetUsed();
    Assert.assertTrue(participateAccountBeforeNetUsed == 0);

    //Participate an assetIssue, then query the net information.
   //Assert.assertTrue(PublicMethed.waitProduceNextBlock(blockingStubFull));
    ByteString assetNameBs = ByteString.copyFrom(name.getBytes());
    GrpcAPI.BytesMessage request1 = GrpcAPI.BytesMessage.newBuilder().setValue(assetNameBs).build();
    Contract.AssetIssueContract assetIssueByName = blockingStubFull.getAssetIssueByName(request1);
    logger.info(Long.toString(assetIssueByName.getStartTime()));
    logger.info(Long.toString(System.currentTimeMillis()));
    
    Assert.assertTrue(PublicMethed.participateAssetIssue(asset007Address,name.getBytes(),
        1L,participateAssetAddress,participateAssetCreateKey,blockingStubFull));

    addressBs = ByteString.copyFrom(asset007Address);
    request = Account.newBuilder().setAddress(addressBs).build();
    asset007NetMessage = blockingStubFull.getAccountNet(request);
    final Long asset007AfterFreeNetUsed = asset007NetMessage.getFreeNetUsed();

    addressBs = ByteString.copyFrom(participateAssetAddress);
    request = Account.newBuilder().setAddress(addressBs).build();
    participateAccountNetMessage = blockingStubFull.getAccountNet(request);
    final Long participateAccountAfterNetUsed = participateAccountNetMessage.getFreeNetUsed();

    logger.info(Long.toString(asset007BeforeFreeNetUsed));
    logger.info(Long.toString(asset007AfterFreeNetUsed));
    logger.info(Long.toString(participateAccountBeforeNetUsed));
    logger.info(Long.toString(participateAccountAfterNetUsed));
    Assert.assertTrue(asset007AfterFreeNetUsed <= asset007BeforeFreeNetUsed);
    Assert.assertTrue(participateAccountAfterNetUsed - participateAccountBeforeNetUsed > 150);



    Assert.assertTrue(PublicMethed.participateAssetIssue(asset007Address,name.getBytes(),
        1L,participateAssetAddress,participateAssetCreateKey,blockingStubFull));
    Assert.assertTrue(PublicMethed.participateAssetIssue(asset007Address,name.getBytes(),
        1L,participateAssetAddress,participateAssetCreateKey,blockingStubFull));

    Account participateInfo = PublicMethed.queryAccount(participateAssetCreateKey,blockingStubFull);
    final Long beforeBalance = participateInfo.getBalance();
    Assert.assertTrue(PublicMethed.participateAssetIssue(asset007Address,name.getBytes(),
        1L,participateAssetAddress,participateAssetCreateKey,blockingStubFull));
    participateInfo = PublicMethed.queryAccount(participateAssetCreateKey,blockingStubFull);
    final Long afterBalance = participateInfo.getBalance();

    Assert.assertTrue(beforeBalance  - trxNum*1*icoNum  > afterBalance);



  }

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


