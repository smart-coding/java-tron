package org.tron.core.net.node;

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
import org.tron.common.overlay.server.Channel;
import org.tron.common.overlay.server.ChannelManager;
import org.tron.common.overlay.server.SyncPool;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.ReflectUtils;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.ByteArrayWrapper;
import org.tron.core.db.Manager;
import org.tron.core.net.message.BlockMessage;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.services.RpcApiService;
import org.tron.core.services.WitnessService;
import org.tron.protos.Protocol;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
public class StartFetchSyncBlockTest {
    private static AnnotationConfigApplicationContext context;
    private NodeImpl node;
    RpcApiService rpcApiService;
    PeerClient peerClient;
    ChannelManager channelManager;
    SyncPool pool;
    private static final String dbPath = "output-nodeImplTest/startFetchSyncBlockTest";

    private class Condition {

        private Sha256Hash blockId;

        public Condition(Sha256Hash blockId) {
            this.blockId = blockId;
        }

        public Sha256Hash getBlockId() {
            return blockId;
        }

    }

    private Sha256Hash testBlockBroad() {
        Protocol.Block block = Protocol.Block.getDefaultInstance();
        BlockMessage blockMessage = new BlockMessage(block);
        node.broadcast(blockMessage);
        ConcurrentHashMap<Sha256Hash, Protocol.Inventory.InventoryType> advObjToSpread = ReflectUtils
                .getFieldValue(node, "advObjToSpread");
        Assert.assertEquals(advObjToSpread.get(blockMessage.getMessageId()), Protocol.Inventory.InventoryType.BLOCK);
        return blockMessage.getMessageId();
    }

    private Condition testConsumerAdvObjToSpread() {
        Sha256Hash blockId = testBlockBroad();

        ReflectUtils.invokeMethod(node, "consumerAdvObjToSpread");
        Collection<PeerConnection> activePeers = ReflectUtils.invokeMethod(node, "getActivePeer");

        boolean result = true;
        for (PeerConnection peerConnection : activePeers) {
            if (!peerConnection.getAdvObjWeSpread().containsKey(blockId)) {
                result &= false;
            }
        }
        for (PeerConnection peerConnection : activePeers) {
            peerConnection.getAdvObjWeSpread().clear();
        }
        Assert.assertTrue(result);
        return new Condition(blockId);
    }

    @Test
    public void testStartFetchSyncBlock() throws InterruptedException {
        testConsumerAdvObjToSpread();
        Collection<PeerConnection> activePeers = ReflectUtils.invokeMethod(node, "getActivePeer");
        Thread.sleep(1000);
        ReflectUtils.setFieldValue(activePeers.iterator().next(),"needSyncFromPeer", true);
        // construct a block
        Protocol.Block block = Protocol.Block.getDefaultInstance();
        BlockMessage blockMessage = new BlockMessage(block);
        // push the block to syncBlockToFetch
        activePeers.iterator().next().getSyncBlockToFetch().push(blockMessage.getBlockId());
        // invoke testing method
        ReflectUtils.invokeMethod(node,"startFetchSyncBlock");
        Map<BlockCapsule.BlockId, Long> syncBlockIdWeRequested = ReflectUtils
                .getFieldValue(node, "syncBlockIdWeRequested");
        Assert.assertTrue(syncBlockIdWeRequested.size()==1);
    }


    private static boolean go = false;

    @Before
    public void init() {
        Thread thread = new Thread(new Runnable() {
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
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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

            org.tron.common.overlay.discover.Node node = new Node(
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
