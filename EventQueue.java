/** Multithreaded Event Queue messaging node.
*/
public class EventQueue<V> {
	/** An event passed around in Queues, owned by the creating queue.
		Created from EventQueue.send
		@see #send(int,String,Object)
		@see #send(int,String,Object,EventQueue)
	*/
	public class Event {
		/** The type of the event.
			@return an arbitrary integer used for message type
		*/
		public int type() {
			return _type;
		}
		/** The name of the event.
			@return an arbitrary String used to identify the contents of the message.
		*/
		public String name() {
			return _name;
		}
		/** The value of the event.
			@return the payload data of the event.
		*/
		public V value() {
			return _value;
		}
		/** The queue to send a response on.
			@return a queue to call send on to give a response. null if no response is needed.
		*/
		public EventQueue<V> results() {
			return _response;
		}
		/** Optimization to prevent the needless recreation of events.
			When you are done with a message, call recycle() on it to keep it around
			for future messages.
		*/
		public void recycle() {
			_recycle.push(this, 1 /* milliseconds to wait if we've reached max recycled events */ );
		}
		/** Use defined meaning, type integer. */
		private int 			_type;
		/** Use defined meaning, event name. */
		private String			_name;
		/** The payload data of the message. */
		private V				_value;
		/** The queue to send a response on. */
		private EventQueue<V>	_response;
		/** Create a new event with the given values.
			@param type 	event type
			@param name		event name
			@param value	event payload
			@param response	the response queue (may be null)
		*/
		protected Event(int type, String name, V value, EventQueue<V> response) {
			set(type, name, value, response);
		}
		/** Sets the values of the event.
			@param type 	event type
			@param name		event name
			@param value	event payload
			@param response	the response queue (may be null)
		*/
		protected void set(int type, String name, V value, EventQueue<V> response) {
			_type= type;
			_name= name;
			_value= value;
			_response= response;
		}
	}
	/**
		@param maxPendingEvents	The maxmimum number of unhandled events in the queue.
		@see #send(int,String,Object)
		@see #send(int,String,Object,EventQueue)
	*/
	public EventQueue(int maxPendingEvents) {
		_queue= new BlockingQueue<Event>(maxPendingEvents);
		_recycle= new BlockingQueue<Event>(1000 /* max recycled nodes */);
	}
	/** Sends an event.
		@param type		User defined integer for the event type
		@param name		User defined name of the event
		@param value	The event payload
		@return			<code>true</code> if the event was successfully sent
	*/
	public boolean send(int type, String name, V value) {
		return send(type, name, value, null);
	}
	/** Sends an event.
		@param type		User defined integer for the event type
		@param name		User defined name of the event
		@param value	The event payload
		@param response	The queue to reply to the event on
		@return			<code>true</code> if the event was successfully sent
	*/
	public boolean send(int type, String name, V value, EventQueue<V> response) {
		Event	toSend= null;

		if(!_recycle.empty()) {
			toSend= _recycle.pop(1 /* milliseconds to wait for a recycled event */ );
		}
		if(null == toSend) {
			toSend= new Event(type, name, value, response);
		} else {
			toSend.set(type, name, value, response);
		}
		return _queue.push(toSend, 10 /* milliseconds to wait if the queue is full */ );
	}
	/** Gets the next pending event.
		@param timeoutInMilliseconds	The amount of time to wait if no event is available
		@return							The next event in the queue
	*/
	public Event next(long timeoutInMilliseconds) {
		return _queue.pop(timeoutInMilliseconds);
	}
	/** The list of recycled events so we don't need to allocate new events. */
	private BlockingQueue<Event>	_recycle;
	/** The event queue. */
	private BlockingQueue<Event>	_queue;
	/** Test.
		If anything is printed to stderr, the test failed.
	*/
	public static void main(String... arg) {
		EventQueue<String>	q1= new EventQueue<String>(1005);
		EventQueue<String>	q2= new EventQueue<String>(1005);
		String				value= "Arbitrary Data";

		for(int i= 0; i < 1005; ++i) {
			if(!q1.send(i, "test", value, q2)) {
				System.err.println("Unable to send #"+(i+1));
			}
		}
		if(q1.send(1006, "test", value, q2)) {
			System.err.println("Able to send #1006");
		}
		for(int i= 0; i < 1005; ++i) {
			EventQueue<String>.Event	event= q1.next(5);

			if(event.type() != i) {
				System.err.println("1 Expected "+i+" but got "+event.type());
			}
			if(!event.name().equals("test")) {
				System.err.println("1 Expected test but got "+event.name());
			}
			if(!event.value().equals(value)) {
				System.err.println("1 Expected "+value+" but got "+event.value());
			}
			event.results().send(i, "test2", value);
			event.recycle();
		}
		for(int i= 0; i < 1005; ++i) {
			EventQueue<String>.Event	event= q2.next(5);

			if(event.type() != i) {
				System.err.println("2 Expected "+i+" but got "+event.type());
			}
			if(!event.name().equals("test2")) {
				System.err.println("2 Expected test2 but got "+event.name());
			}
			if(!event.value().equals(value)) {
				System.err.println("2 Expected "+value+" but got "+event.value());
			}
			event.recycle();
		}
	}
}
