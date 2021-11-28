package bilayer;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import enums.MessageEnum;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.TimerManager;

import static constant.ConstantValue.*;

public class bilayerBFTNode {

    Logger logger = LoggerFactory.getLogger(getClass());

    private int n;                                                  // 总节点数
    private int maxF;                                               // 最大容错数, 对应论文中的f
    private int index;                                              // 该节点的标识
    private bilayerBFTMsg curREQMsg;                                // 当前正在处理的请求
    private long SendWeightTime = SEND_WEIGHT_TIME;                 // 隔多久发送WEIGHT消息, 根据实际情况调整
    private long SendNoBlockTime = SEND_NO_BLOCK_TIME;              // 隔多久发送NO_BLOCK消息, 根据实际情况调整
    public double totalSendMsgLen = 0;                              // 发送的所有消息的长度之和
    private volatile boolean isRunning = false;                     // 是否正在运行, 可用于设置Crash节点

    private int groupSize;                                          // 组大小
    private int groupMaxF;                                          // 组中的容错数量
    private boolean isLeader;                                       // 是否是leader
    private int weight;                                             // 用于是leader时的权重记录


    // 消息队列
    private BlockingQueue<bilayerBFTMsg> qbm = Queues.newLinkedBlockingQueue();

    // RBC解码后区块记录
    private Set<String> REQMsgRecord = Sets.newConcurrentHashSet();

    // 准备阶段消息记录
    private Set<String> PAMsgRecord = Sets.newConcurrentHashSet();
    // 记录已经收到的PA消息对应的数量
    private AtomicLongMap<String> PAMsgCountMap = AtomicLongMap.create();

    // 提交阶段消息记录
    private Set<String> CMMsgRecord = Sets.newConcurrentHashSet();
    // 记录已经收到的CM消息对应的数量
    private AtomicLongMap<String> CMMsgCountMap = AtomicLongMap.create();

    // 回复消息数量
    private AtomicLong replyMsgCount = new AtomicLong();

    // 已经发起过受理的请求
    private Map<String, bilayerBFTMsg> applyMsgRecord = Maps.newConcurrentMap();
    // 已经成功处理过的请求
    private Map<String,bilayerBFTMsg> doneMsgRecord = Maps.newConcurrentMap();

    // 存入client利用RBC发出区块的时间, 用于判断何时发送WEIGHT和NO_BLOCK消息
    private Map<String,Long> REQMsgTimeout = Maps.newHashMap();

    // 请求队列
    private BlockingQueue<bilayerBFTMsg> reqQueue = Queues.newLinkedBlockingDeque();

    // 权重累加值
    private AtomicLong WeightSum = new AtomicLong();

    private Timer timer;

    public bilayerBFTNode(int index, int n, int groupSize, boolean isLeader) {
        this.index = index;
        this.n = n;
        this.maxF = (n-1) / 3;
        this.groupSize = groupSize;
        this.groupMaxF = (groupSize-1) / 3;
        this.isLeader = isLeader;
        timer = new Timer("Timer"+index);
    }

    public bilayerBFTNode start() {
        // TODO 待补充
        new Thread(() -> {
            while(true) {
                try {
                    bilayerBFTMsg msg = qbm.take();
                    doAction(msg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        isRunning = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                doReq();
            }
        }, 100, 100);
        return this;
    }

    private boolean doAction(bilayerBFTMsg msg) {
        if(!isRunning) return false;
        if(msg != null) {
            logger.info("[节点" + index + "]收到消息:"+ msg);
            switch (msg.getType()) {
                case REQUEST:
                    onRequest(msg);
                    break;
                case PREPARE:
                    onPrepare(msg);
                    break;
                case COMMIT:
                    onCommit(msg);
                    break;
                case REPLY:
//                    onReply(msg);
                    break;
                default:
                    break;
            }
            return true;
        }
        return false;
    }

    private void onRequest(bilayerBFTMsg msg) {
        if(!msg.isValid()) {
            logger.info("[节点" + index + "]收到异常消息" + msg);
            return;
        }
        if(applyMsgRecord.containsKey(msg.getDataKey())) return; // 已经受理过
        // PBFT中忽略了自己发的请求, 这边怎么处理暂时待定 [?]
        applyMsgRecord.put(msg.getDataKey(), msg);
        // 根据第一层共识算法, 直接广播prepare消息
        bilayerBFTMsg PAMsg = new bilayerBFTMsg(msg);
        PAMsg.setType(MessageEnum.PREPARE);
        PAMsg.setSenderId(index);
        // 组内广播PA消息
        publishInsideGroup(PAMsg);
    }

    private void onPrepare(bilayerBFTMsg msg) {
        if(!checkMsg(msg)) {
            logger.info("[节点" + index + "]收到异常消息" + msg);
            return;
        }
        String msgKey = msg.getMsgKey();
        if(PAMsgRecord.contains(msgKey)) {
            // 说明已经投过票, 不能重复投
            return;
        }
        // 记录收到的PAMsg
        PAMsgRecord.add(msgKey);
        // 票数+1, 并返回+1后的票数
        long agCou = PAMsgCountMap.incrementAndGet(msg.getDataKey());
        if(agCou >= 2*groupMaxF + 1) {
            PAMsgCountMap.remove(msg.getDataKey());
            bilayerBFTMsg CMMsg = new bilayerBFTMsg(msg);
            CMMsg.setType(MessageEnum.COMMIT);
            CMMsg.setSenderId(index);
            doneMsgRecord.put(CMMsg.getDataKey(), CMMsg);
            publishInsideGroup(CMMsg);
        }
    }

    private void onCommit(bilayerBFTMsg msg) {
        if(!checkMsg(msg)) {
            logger.info("[节点" + index + "]收到异常消息" + msg);
            return;
        }
        String msgKey = msg.getMsgKey();
        if(CMMsgRecord.contains(msgKey)) {
            // 已经投过票, 不能重复投
            return;
        }
        if(!PAMsgRecord.contains(msgKey)) {
            // 必须先过准备阶段
            return;
        }
        // 记录收到的CMMsg
        CMMsgRecord.add(msgKey);
        // 票数+1, 并返回+1后的票数
        long agCou = CMMsgCountMap.incrementAndGet(msg.getDataKey());
        if(agCou == 2*groupMaxF + 1) { // 改成等于, 只在到2f+1的那一次发REPLY
            // 和PBFT不同, 这里先不执行请求, 只发送REPLY消息, 等到最终共识结果出来再执行请求
            bilayerBFTMsg REPLYMsg = new bilayerBFTMsg(msg);
            REPLYMsg.setType(MessageEnum.REPLY);
            REPLYMsg.setSenderId(index);
            // 发送REPLY消息给leader
            send(getLeaderIndex(index), REPLYMsg);
        }
    }




    // 执行对应请求
    private void doSomething(bilayerBFTMsg msg) {
        logger.info("[节点" + index + "]成功执行请求" + msg);
    }

    // 请求入列
    public void req(String data) {
        bilayerBFTMsg REQMsg = new bilayerBFTMsg(MessageEnum.REQUEST, index);
        REQMsg.setDataHash(data);
        try {
            reqQueue.put(REQMsg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 检查请求
    private boolean doReq() {
        if(curREQMsg != null) return false; // 上一个请求还未完成
        curREQMsg = reqQueue.poll();
        if(curREQMsg == null) return false;
        doSendCurMsg();
        return true;
    }

    // 发送当前请求
    private void doSendCurMsg() {
        // 记录通过RBC发送时间, 用于后续判断何时发送WEIGHT和NO_BLOCK消息
        REQMsgTimeout.put(curREQMsg.getDataHash(), System.currentTimeMillis());
        doRBC();
    }

    // 通过RBC把请求发给所有节点
    private void doRBC() {
        // TODO 补充RBC, 效果为所有节点的qbm中加入请求
        publishToAll(curREQMsg);
    }

    // 向所有节点广播 (组内组外)
    public synchronized void publishToAll(bilayerBFTMsg msg) {
        logger.info("[节点" + index + "]向所有节点广播消息:" + msg);
        for(int i = 0; i < n; i++) {
            send(bilayerBFTMain.node2Index[i], new bilayerBFTMsg(msg));
        }
    }

    // 组内广播
    public synchronized void publishInsideGroup(bilayerBFTMsg msg) {
        logger.info("[节点" + index + "]组内广播消息:" + msg);
        int leaderIndex = getLeaderIndex(index);
        for(int i = 0; i < groupSize; i++) {
            send(leaderIndex + i, new bilayerBFTMsg(msg));
        }
    }

    public synchronized void send(int toIndex, bilayerBFTMsg msg) {
        // 模拟发送时长
        try {
            Thread.sleep(sendMsgTime(msg, BANDWIDTH));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        totalSendMsgLen += msg.getMsgLen();

        // 模拟网络延迟
        TimerManager.schedule(() -> {
            bilayerBFTMain.nodes[bilayerBFTMain.index2Node[toIndex]].pushMsg(msg);
            return null;
        },bilayerBFTMain.netDelay[index][toIndex]);

    }

    public long sendMsgTime(bilayerBFTMsg msg, int bandwidth) {
        return msg.getMsgLen() * 1000 / bandwidth;
    }

    public void pushMsg(bilayerBFTMsg msg) {
        try {
            qbm.put(msg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean checkMsg(bilayerBFTMsg msg) {
        return (msg.isValid()
                // 如果是自己的消息无需校验
                // 收到的消息是自己组的
                && (msg.getSenderId() == index || getLeaderIndex(index) == getLeaderIndex(msg.getSenderId())));
    }

    private void cleanCache(String it) {
        PAMsgRecord.removeIf((vp) -> StringUtils.startsWith(vp, it));
        CMMsgRecord.removeIf((vp) -> StringUtils.startsWith(vp, it));
        PAMsgCountMap.remove(it);
        CMMsgRecord.remove(it);
    }

    // 为了方便, 将节点序号隔OFFSET个分为一组, 第一个能被OFFSET整除的序号对应的是leader
    public int getLeaderIndex(int index) {
        if(n < 32) return 0;
        return index / OFFSET * OFFSET;
    }

    private void NodeCrash(){
        logger.info("[节点" + index + "]宕机--------------");
        isRunning = false;
    }

    private void NodeRecover() {
        logger.info("[节点" + index + "]恢复--------------");
        isRunning = true;
    }

}