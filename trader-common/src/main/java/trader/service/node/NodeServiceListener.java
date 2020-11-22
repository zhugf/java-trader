package trader.service.node;

public interface NodeServiceListener {

    public void onSessionAdded(NodeSession session);

    public void onSessionClosed(NodeSession session);
}
