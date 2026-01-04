package com.notification.service.channel;

// =====================================================
// ChannelDispatcher.java - Routes to Correct Handler
// =====================================================
//
// The dispatcher's job is to:
// 1. Look at the notification's channel
// 2. Find the right handler
// 3. Call it to send the notification
//
// This is a simple ROUTING component.
//

import com.notification.model.entity.Notification;
import com.notification.model.enums.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispatches notifications to the appropriate channel handler.
 */
@Service
public class ChannelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChannelDispatcher.class);
    
    /**
     * Map of channel type to handler.
     * 
     * Using a Map allows O(1) lookup of handlers.
     */
    private final Map<ChannelType, ChannelHandler> handlers;
    
    /**
     * Constructor injection of all channel handlers.
     * 
     * Spring automatically injects ALL beans that implement ChannelHandler.
     * We then organize them into a map for easy lookup.
     * 
     * @param handlerList List of all ChannelHandler implementations
     */
    public ChannelDispatcher(List<ChannelHandler> handlerList) {
        // Convert list to map: ChannelType -> Handler
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(
                ChannelHandler::getChannelType,  // Key: channel type
                Function.identity()              // Value: the handler itself
            ));
        
        log.info("Registered {} channel handlers: {}", 
            handlers.size(), handlers.keySet());
    }
    
    /**
     * Dispatch a notification to the appropriate handler.
     * 
     * @param notification The notification to send
     * @return true if sent successfully
     */
    public boolean dispatch(Notification notification) {
        ChannelType channel = notification.getChannel();
        
        ChannelHandler handler = handlers.get(channel);
        
        if (handler == null) {
            log.error("No handler found for channel: {}", channel);
            return false;
        }
        
        if (!handler.canHandle(notification)) {
            log.error("Handler {} cannot process notification {}", 
                channel, notification.getId());
            return false;
        }
        
        log.debug("Dispatching notification {} to {} handler", 
            notification.getId(), channel);
        
        return handler.send(notification);
    }
}
