package org.tron.core.net.node;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.overlay.client.PeerClient;
import org.tron.common.overlay.discover.Node;
import org.tron.common.overlay.message.Message;
import org.tron.common.overlay.server.Channel;
import org.tron.common.overlay.server.ChannelManager;
import org.tron.common.overlay.server.MessageQueue;
import org.tron.common.overlay.server.SyncPool;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.ReflectUtils;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.ByteArrayWrapper;
import org.tron.core.db.Manager;
import org.tron.core.net.message.BlockMessage;
import org.tron.core.net.message.MessageTypes;
import org.tron.core.net.message.TransactionMessage;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.services.RpcApiService;
import org.tron.core.services.WitnessService;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.Inventory.InventoryType;
import org.tron.protos.Protocol.Transaction;

@Slf4j
public class BroadTest {

  private static AnnotationConfigApplicationContext context;
  private NodeImpl node;
  RpcApiService rpcApiService;
  PeerClient peerClient;
  ChannelManager channelManager;
  SyncPool pool;
  private static final String dbPath = "output-nodeImplTest/broad";

  private class Condition {

    private Sha256Hash blockId;
    private Sha256Hash transactionId;

    public Condition(Sha256Hash blockId, Sha256Hash transactionId) {
      this.blockId = blockId;
      this.transactionId = transactionId;
    }

    public Sha256Hash getBlockId() {
      return blockId;
    }

    public Sha256Hash getTransactionId() {
      return transactionId;
    }

  }

  private Sha256Hash testBlockBroad() {
    Block block = Block.getDefaultInstance();
    BlockMessage blockMessage = new BlockMessage(block);
    node.broadcast(blockMessage);
    ConcurrentHashMap<Sha256Hash, InventoryType> advObjToSpread = ReflectUtils
        .getFieldValue(node, "advObjToSpread");
    Assert.assertEquals(advObjToSpread.get(blockMessage.getMessageId()), InventoryType.BLOCK);
    return blockMessage.getMessageId();
  }

  private Sha256Hash testTransactionBroad() {
    Transaction transaction = Transaction.getDefaultInstance();
    TransactionMessage transactionMessage = new TransactionMessage(transaction);
    node.broadcast(transactionMessage);
    ConcurrentHashMap<Sha256Hash, InventoryType> advObjToSpread = ReflectUtils
        .getFieldValue(node, "advObjToSpread");
    Assert.assertEquals(advObjToSpread.get(transactionMessage.getMessageId()), InventoryType.TRX);
    return transactionMessage.getMessageId();
  }

  private Condition testConsumerAdvObjToSpread() {
    Sha256Hash blockId = testBlockBroad();
    Sha256Hash transactionId = testTransactionBroad();

    ReflectUtils.invokeMethod(node, "consumerAdvObjToSpread");
    Collection<PeerConnection> activePeers = ReflectUtils.invokeMethod(node, "getActivePeer");

    boolean result = true;
    for (PeerConnection peerConnection : activePeers) {
      if (!peerConnection.getAdvObjWeSpread().containsKey(blockId)) {
        result &= false;
      }
      if (!peerConnection.getAdvObjWeSpread().containsKey(transactionId)) {
        result &= false;
      }
    }
    for (PeerConnection peerConnection : activePeers) {
      peerConnection.getAdvObjWeSpread().clear();
    }
    Assert.assertTrue(result);
    return new Condition(blockId, transactionId);
  }

  @Test
  public void testConsumerAdvObjToFetch() throws InterruptedException {
    Condition condition = testConsumerAdvObjToSpread();
    Thread.sleep(1000);
    //
    ConcurrentHashMap<Sha256Hash, InventoryType> advObjToFetch = ReflectUtils
        .getFieldValue(node, "advObjToFetch");
    logger.info("advObjToFetch:{}", advObjToFetch);
    Assert.assertEquals(advObjToFetch.get(condition.getBlockId()), InventoryType.BLOCK);
    Assert.assertEquals(advObjToFetch.get(condition.getTransactionId()), InventoryType.TRX);
    //To avoid writing the database, manually stop the sending of messages.
    Collection<PeerConnection> activePeers = ReflectUtils.invokeMethod(node, "getActivePeer");
    for (PeerConnection peerConnection : activePeers) {
      MessageQueue messageQueue = ReflectUtils.getFieldValue(peerConnection, "msgQueue");
      ReflectUtils.setFieldValue(messageQueue, "sendMsgFlag", false);
    }
    //
    ReflectUtils.invokeMethod(node, "consumerAdvObjToFetch");
    Thread.sleep(1000);
    boolean result = true;
    int count = 0;
    for (PeerConnection peerConnection : activePeers) {
      if (peerConnection.getAdvObjWeRequested().containsKey(condition.getTransactionId())) {
        ++count;
      }
      if (peerConnection.getAdvObjWeRequested().containsKey(condition.getBlockId())) {
        ++count;
      }
      MessageQueue messageQueue = ReflectUtils.getFieldValue(peerConnection, "msgQueue");
      BlockingQueue<Message> msgQueue = ReflectUtils.getFieldValue(messageQueue, "msgQueue");
      for (Message message : msgQueue) {
        if (message.getType() == MessageTypes.BLOCK) {
          Assert.assertEquals(message.getMessageId(), condition.getBlockId());
        }
        if (message.getType() == MessageTypes.TRX) {
          Assert.assertEquals(message.getMessageId(), condition.getTransactionId());
        }
      }
    }
    Assert.assertTrue(count >= 2);
  }

  private static boolean go = false;

  @Before
  public void init() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        logger.info("Full node running.");
        Args.setParam(new String[]{"-d",dbPath}, "config.conf");
        Args cfgArgs = Args.getInstance();
        cfgArgs.setNodeListenPort(17889);
        cfgArgs.setNodeDiscoveryEnable(false);
        cfgArgs.getSeedNode().getIpList().clear();
        cfgArgs.setNeedSyncCheck(false);
        cfgArgs.setNodeExternalIp("127.0.0.1");

        context = new AnnotationConfigApplicationContext(DefaultConfig.class);

        if (cfgArgs.isHelp()) {
          logger.info("Here is the help message.");
          return;
        }
        Application appT = ApplicationFactory.create(context);
        rpcApiService = context.getBean(RpcApiService.class);
        appT.addService(rpcApiService);
        if (cfgArgs.isWitness()) {
          appT.addService(new WitnessService(appT));
        }
//        appT.initServices(cfgArgs);
//        appT.startServices();
//        appT.startup();
        node = context.getBean(NodeImpl.class);
        peerClient = context.getBean(PeerClient.class);
        channelManager = context.getBean(ChannelManager.class);
        pool = context.getBean(SyncPool.class);
        Manager dbManager = context.getBean(Manager.class);
        NodeDelegate nodeDelegate = new NodeDelegateImpl(dbManager);
        node.setNodeDelegate(nodeDelegate);
        prepare();
        rpcApiService.blockUntilShutdown();
      }
    }).start();
    int tryTimes = 0;
    while (tryTimes < 10 && (node == null || peerClient == null
        || channelManager == null || pool == null || !go)) {
      try {
        logger.info("node:{},peerClient:{},channelManager:{},pool:{},{}", node, peerClient,
            channelManager, pool, go);
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      } finally {
        ++tryTimes;
      }
    }
  }

  private void prepare() {
    try {
      ExecutorService advertiseLoopThread = ReflectUtils.getFieldValue(node, "broadPool");
      advertiseLoopThread.shutdownNow();

      ReflectUtils.setFieldValue(node, "isAdvertiseActive", false);
      ReflectUtils.setFieldValue(node, "isFetchActive", false);

      ScheduledExecutorService mainWorker = ReflectUtils
          .getFieldValue(channelManager, "mainWorker");
      mainWorker.shutdownNow();

      Node node = new Node(
          "enode://e437a4836b77ad9d9ffe73ee782ef2614e6d8370fcf62191a6e488276e23717147073a7ce0b444d485fff5a0c34c4577251a7a990cf80d8542e21b95aa8c5e6c@127.0.0.1:17889");
      new Thread(new Runnable() {
        @Override
        public void run() {
          peerClient.connect(node.getHost(), node.getPort(), node.getHexId());
        }
      }).start();
      Thread.sleep(1000);
      List<Channel> newChanelList = ReflectUtils.getFieldValue(channelManager, "newPeers");
      int tryTimes = 0;
      while (CollectionUtils.isEmpty(newChanelList) && ++tryTimes < 10) {
        Thread.sleep(1000);
      }
      logger.info("newChanelList size : {}", newChanelList.size());

      Field activePeersField = channelManager.getClass().getDeclaredField("activePeers");
      activePeersField.setAccessible(true);
      Map<ByteArrayWrapper, Channel> activePeersMap = (Map<ByteArrayWrapper, Channel>) activePeersField
          .get(channelManager);

      Field apField = pool.getClass().getDeclaredField("activePeers");
      apField.setAccessible(true);
      List<PeerConnection> activePeers = (List<PeerConnection>) apField.get(pool);

      for (Channel channel : newChanelList) {
        activePeersMap.put(channel.getNodeIdWrapper(), channel);
        activePeers.add((PeerConnection) channel);
      }
      apField.set(pool, activePeers);
      activePeersField.set(channelManager, activePeersMap);
      //
      go = true;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @After
  public void destroy() {
    FileUtil.deleteDir(new File("output-nodeImplTest"));
  }

}
