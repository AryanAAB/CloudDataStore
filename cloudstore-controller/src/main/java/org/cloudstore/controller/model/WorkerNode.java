package org.cloudstore.controller.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tracks worker liveness, network address, and storage allocation state in persistent memory.
 */
@Entity
@Table(name = "worker_nodes")
public class WorkerNode
{
    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";

    @Id
    @Column(name = "worker_id", nullable = false, length = 128)
    private String workerId;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "total_capacity_bytes", nullable = false)
    private long totalCapacityBytes;

    @Column(name = "unassigned_capacity_bytes", nullable = false)
    private long unassignedCapacityBytes;

    public WorkerNode()
    {
    }

    public WorkerNode(String workerId, String address, long totalCapacityBytes)
    {
        this.workerId = workerId;
        this.address = address;
        this.status = STATUS_UP;
        this.lastHeartbeat = Instant.now();
        this.totalCapacityBytes = totalCapacityBytes;
        this.unassignedCapacityBytes = totalCapacityBytes;
    }

    public String getWorkerId()
    {
        return workerId;
    }

    public void setWorkerId(String workerId)
    {
        this.workerId = workerId;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Instant getLastHeartbeat()
    {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat)
    {
        this.lastHeartbeat = lastHeartbeat;
    }

    public long getTotalCapacityBytes()
    {
        return totalCapacityBytes;
    }

    public void setTotalCapacityBytes(long totalCapacityBytes)
    {
        this.totalCapacityBytes = totalCapacityBytes;
    }

    public long getUnassignedCapacityBytes()
    {
        return unassignedCapacityBytes;
    }

    public void setUnassignedCapacityBytes(long unassignedCapacityBytes)
    {
        this.unassignedCapacityBytes = unassignedCapacityBytes;
    }

    public boolean isUp()
    {
        return STATUS_UP.equalsIgnoreCase(status);
    }
}

