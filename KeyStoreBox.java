/** KeyStore used as a message box, like a post box.
*/
public class KeyStoreBox extends KeyStoreNode {
	public KeyStoreBox(int maxPendingEvents) {
		super(maxPendingEvents);
	}
	public Event next(int timeoutInMilliseconds) {
		return _next(timeoutInMilliseconds);
	}
	public void recycle(Event e) {
		_recycleEvent(e);
	}
}
