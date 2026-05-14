package com.example;

public class LegacyMachineSimulator {

    public enum MachineState {
        IDLE,
        RUNNING,
        STOPPED,
        ERROR
    }

    private MachineState currentState = MachineState.IDLE;
    private boolean running = false;
    private int errorCode = 0;

    private double targetSpeed = 100.0;
    private double accelerationLimit = 10.0;

    private boolean idle = true;
    private boolean fault = false;
    private String operationMode = "AUTO";
    private double temperature = 24.5;
    private String connectionHealth = "GOOD";
    private boolean cycleActive = false;

    private int timeout = 5000;
    private int retryCount = 3;
    private double threshold = 75.0;

    private int warningCode = 0;
    private int communicationRetryCounter = 0;
    private final long startTimeMillis = System.currentTimeMillis();

    private String deviceIdentity = "FeederController-Prototype-01";

    public MachineState getCurrentState() {
        return currentState;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isIdle() {
        return idle;
    }

    public boolean hasFault() {
        return fault;
    }

    public String getOperationMode() {
        return operationMode;
    }

    public double getTemperature() {
        return temperature;
    }

    public String getConnectionHealth() {
        return connectionHealth;
    }

    public boolean isCycleActive() {
        return cycleActive;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getWarningCode() {
        return warningCode;
    }

    public int getCommunicationRetryCounter() {
        return communicationRetryCounter;
    }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000;
    }

    public String getDeviceIdentity() {
        return deviceIdentity;
    }

    public double getTargetSpeed() {
        return targetSpeed;
    }

    public void setTargetSpeed(double targetSpeed) {
        this.targetSpeed = targetSpeed;
    }

    public double getAccelerationLimit() {
        return accelerationLimit;
    }

    public void setAccelerationLimit(double accelerationLimit) {
        this.accelerationLimit = accelerationLimit;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public void start() {
        if (currentState == MachineState.IDLE || currentState == MachineState.STOPPED) {
            currentState = MachineState.RUNNING;
            running = true;
            idle = false;
            fault = false;
            cycleActive = true;
            errorCode = 0;
        }
    }

    public void stop() {
        currentState = MachineState.STOPPED;
        running = false;
        idle = false;
        cycleActive = false;
    }

    public void reset() {
        currentState = MachineState.IDLE;
        running = false;
        idle = true;
        fault = false;
        cycleActive = false;
        errorCode = 0;
        warningCode = 0;
    }

    public void pause() {
        if (currentState == MachineState.RUNNING) {
            currentState = MachineState.STOPPED;
            running = false;
            idle = false;
            cycleActive = false;
        }
    }

    public void resume() {
        if (currentState == MachineState.STOPPED) {
            currentState = MachineState.RUNNING;
            running = true;
            idle = false;
            fault = false;
            cycleActive = true;
        }
    }

    public void home() {
        currentState = MachineState.IDLE;
        running = false;
        idle = true;
        fault = false;
        cycleActive = false;
    }

    public void triggerError(int code) {
        currentState = MachineState.ERROR;
        running = false;
        idle = false;
        fault = true;
        cycleActive = false;
        errorCode = code;
        warningCode = code;
    }
}