package trader.service.node;

/**
 * 节点客户端服务, 用于主动通过websocket连接节点管理服务
 */
public interface NodeClientChannel extends NodeConstants {

    public NodeState getState();

}
