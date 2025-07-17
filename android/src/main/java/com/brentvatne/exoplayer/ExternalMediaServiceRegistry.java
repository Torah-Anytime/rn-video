package com.brentvatne.exoplayer;

import android.util.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for managing external media services
 * This class manages the registration and lifecycle of external media services
 * like Android Auto, Android TV, etc. It provides a centralized way to track
 * which services are connected and active.
 */
public class ExternalMediaServiceRegistry {
    private static final String TAG = "ExternalMediaServiceRegistry";
    private static volatile ExternalMediaServiceRegistry instance;

    private final Map<String, ExternalMediaServiceInterface> services;
    private final List<RegistryListener> listeners;

    /**
     * Interface for listening to service registry changes
     */
    public interface RegistryListener {
        void onServiceRegistered(String serviceId, ExternalMediaServiceInterface service);
        void onServiceUnregistered(String serviceId);
    }

    private ExternalMediaServiceRegistry() {
        services = new ConcurrentHashMap<>();
        listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Get the singleton instance of the registry
     * @return The registry instance
     */
    public static ExternalMediaServiceRegistry getInstance() {
        if (instance == null) {
            synchronized (ExternalMediaServiceRegistry.class) {
                if (instance == null) {
                    instance = new ExternalMediaServiceRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Register an external media service
     * @param service The service to register
     * @throws IllegalArgumentException if service is null or has invalid ID
     */
    public void registerService(ExternalMediaServiceInterface service) {
        if (service == null) {
            throw new IllegalArgumentException("Service cannot be null");
        }

        String serviceId = service.getServiceId();
        if (serviceId == null || serviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Service ID cannot be null or empty");
        }

        services.put(serviceId, service);
        Log.d(TAG, "Registered external media service: " + serviceId);

        // Notify listeners
        for (RegistryListener listener : listeners) {
            try {
                listener.onServiceRegistered(serviceId, service);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying registry listener", e);
            }
        }

        // Connect the service to centralized manager if available
        try {
            CentralizedPlaybackManager manager = CentralizedPlaybackManager.getInstance();
            if (manager != null && manager.isInitialized()) {
                service.onConnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "CentralizedPlaybackManager not available during service registration");
        }
    }

    /**
     * Unregister an external media service
     * @param serviceId The ID of the service to unregister
     */
    public void unregisterService(String serviceId) {
        ExternalMediaServiceInterface service = services.remove(serviceId);
        if (service != null) {
            Log.d(TAG, "Unregistered external media service: " + serviceId);

            // Disconnect the service
            try {
                service.onDisconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting service: " + serviceId, e);
            }

            // Notify listeners
            for (RegistryListener listener : listeners) {
                try {
                    listener.onServiceUnregistered(serviceId);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying registry listener", e);
                }
            }
        }
    }

    /**
     * Get a registered service by ID
     * @param serviceId The service ID to look up
     * @return The service interface, or null if not found
     */
    public ExternalMediaServiceInterface getService(String serviceId) {
        return services.get(serviceId);
    }

    /**
     * Add a registry listener
     * @param listener The listener to add
     */
    public void addListener(RegistryListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a registry listener
     * @param listener The listener to remove
     */
    public void removeListener(RegistryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Check if any external services are connected
     * @return true if at least one service is connected
     */
    public boolean hasConnectedServices() {
        return services.values().stream().anyMatch(ExternalMediaServiceInterface::isConnected);
    }

    /**
     * Get count of connected services
     * @return Number of currently connected services
     */
    public int getConnectedServiceCount() {
        return (int) services.values().stream().filter(ExternalMediaServiceInterface::isConnected).count();
    }

    /**
     * Clear all registered services
     * This should only be used during app shutdown
     */
    public void clearAll() {
        Log.d(TAG, "Clearing all registered services");

        // Disconnect all services
        for (ExternalMediaServiceInterface service : services.values()) {
            try {
                service.onDisconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting service during clearAll", e);
            }
        }

        services.clear();
        listeners.clear();
    }

    /**
     * Get registry statistics for debugging
     * @return Map with registry statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("registered_services", services.size());
        stats.put("connected_services", getConnectedServiceCount());
        stats.put("listeners", listeners.size());
        stats.put("service_ids", services.keySet());
        return stats;
    }
}
