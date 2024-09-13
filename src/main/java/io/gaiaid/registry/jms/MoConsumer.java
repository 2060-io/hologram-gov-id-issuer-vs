package io.gaiaid.registry.jms;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.gaiaid.registry.svc.GaiaService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.twentysixty.sa.client.jms.AbstractConsumer;
import io.twentysixty.sa.client.model.event.ConnectionStateUpdated;
import io.twentysixty.sa.client.model.event.Event;
import io.twentysixty.sa.client.model.event.MessageReceived;
import io.twentysixty.sa.client.model.event.MessageState;
import io.twentysixty.sa.client.model.event.MessageStateUpdated;
import io.twentysixty.sa.client.model.message.BaseMessage;
import io.twentysixty.sa.client.model.message.MessageReceiptOptions;
import io.twentysixty.sa.client.model.message.ReceiptsMessage;
import io.twentysixty.sa.client.util.JsonUtil;

@ApplicationScoped
public class MoConsumer extends AbstractConsumer<Event> {

	@Inject GaiaService gaiaService;

	@Inject
    ConnectionFactory _connectionFactory;

	
	@ConfigProperty(name = "io.gaiaid.jms.ex.delay")
	Long _exDelay;
	
	
	@ConfigProperty(name = "io.gaiaid.jms.mo.queue.name")
	String _queueName;
	
	@ConfigProperty(name = "io.gaiaid.jms.mo.consumer.threads")
	Integer _threads;
	
	@ConfigProperty(name = "io.gaiaid.debug")
	Boolean _debug;
	
	private static final Logger logger = Logger.getLogger(MoConsumer.class);
	
	
	void onStart(@Observes StartupEvent ev) {
    	
		logger.info("onStart: SaConsumer queueName: " + _queueName);
		
		this.setExDelay(_exDelay);
		this.setDebug(_debug);
		this.setQueueName(_queueName);
		this.setThreads(_threads);
		this.setConnectionFactory(_connectionFactory);
		super._onStart();
		
    }

    void onStop(@Observes ShutdownEvent ev) {
    	
    	logger.info("onStop: SaConsumer");
    	
    	super._onStop();
    	
    }
	
    @Override
	public void receiveMessage(Event event) throws Exception {
		
    	if (event instanceof MessageReceived) {
    		BaseMessage message = ((MessageReceived) event).getMessage();
    		
    		gaiaService.userInput(message);
    		
    		List<MessageReceiptOptions> receipts = new ArrayList<>();

    		

    		MessageReceiptOptions viewed = new MessageReceiptOptions();
    		viewed.setMessageId(message.getId());
    		viewed.setTimestamp(Instant.now());
    		viewed.setState(MessageState.VIEWED);
    		receipts.add(viewed);

    		ReceiptsMessage r = new ReceiptsMessage();
    		r.setConnectionId(message.getConnectionId());
    		r.setReceipts(receipts);

    		
    		
    		try {
    			// mtProducer.sendMessage(r);
    		} catch (Exception e) {
    			logger.error("", e);
    		}

    	} else if (event instanceof MessageStateUpdated) {
    		// MessageStateUpdated msu = (MessageStateUpdated) event;
    		
    		
    	} else if (event instanceof ConnectionStateUpdated) {
    		ConnectionStateUpdated csu = (ConnectionStateUpdated) event;
    		
    		switch (csu.getState()) {
    		case COMPLETED: {
    			gaiaService.newConnection(csu);
    			break;
    		}
    		case TERMINATED: {
    			gaiaService.deleteConnection(csu);
    			break;
    		}
    		default: {
    			logger.warn("receiveMessage: ignoring message (not implemented) " + JsonUtil.serialize(csu, false));
    		}
    		}
    		
    	}
    	
    	

	}


    private Object controlerLockObj = new Object();
    private boolean started = false;
    private boolean stopped = true;
    
    public void start() {
    	logger.info("start: starting Service Agent Consumers [MoConsumer]");
    	synchronized (controlerLockObj) {
    		try {
    			started = true;
    			super._onStart();
    			stopped = false;
    		} catch (Exception e) {
    			logger.error("start: ", e);
    		}
    	}
    	
    }
    
    public void stop() {
    	logger.info("stop: stopping Service Agent Consumers [MoConsumer]");
    	synchronized (controlerLockObj) {
    		try {
    			stopped = true;
    			super._onStop();
    			started = false;
    		} catch (Exception e) {
    			logger.error("start: ", e);
    		}
    	}
    	
    }

	public boolean isStarted() {
		
		
		synchronized (controlerLockObj) {
			return started;
		}
	}

	

	public boolean isStopped() {
		synchronized (controlerLockObj) {
			return stopped;
		}
		
	}

}

