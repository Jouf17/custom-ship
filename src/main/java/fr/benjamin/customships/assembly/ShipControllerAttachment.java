package fr.benjamin.customships.assembly;

import org.joml.Matrix4dc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import net.minecraft.core.Direction;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;

/**
 * VS2 physics attachment that drives ship movement each physics tick.
 *
 * Controls:
 *  - desiredFwdSpeed : forward/backward speed in ship-local frame (Z axis)
 *  - desiredVy       : vertical speed in world frame
 *  - desiredYawRate  : target angular velocity around world Y (rad/s)
 *
 * Written from the game thread (volatile), read from the physics thread.
 */
public final class ShipControllerAttachment implements ShipPhysicsListener {

    public static final int BLOCKS_PER_STABILIZER = 50;

    private volatile double desiredFwdSpeed = 0.0;
    private volatile double desiredVy       = 0.0;
    private volatile double desiredYawRate  = 0.0;
    private volatile boolean piloted = false;
    private volatile int blockCount = 50;
    private volatile int coreCount = 1;
    private volatile int reactorCount = 0;
    private volatile int stabilizerCount = 0;
    private volatile double forwardX = 0.0;
    private volatile double forwardZ = 1.0;
    private volatile int throttleLevel = 1;

    private double currentFwdSpeed = 0.0;
    private double currentVy = 0.0;
    private double currentYawRate = 0.0;

    private static final double KP_LIN  = 1.6;   // lower value = more drift and slower braking
    private static final double KP_YAW  = 6.0;   // lower value = heavier turning inertia
    private static final double KP_ROT  = 20.0;  // pitch/roll damping gain
    private static final double KP_LEVEL = 10.0;
    private static final double GRAVITY = 9.8;

    public void setDesiredFwdSpeed(double v) { this.desiredFwdSpeed = v; }
    public void setDesiredVy(double v)       { this.desiredVy = v; }
    public void setDesiredYawRate(double v)  { this.desiredYawRate = v; }
    public void setPiloted(boolean value)    { this.piloted = value; }

    public int setThrottleLevel(int requestedLevel) {
        throttleLevel = Math.max(1, Math.min(getMaxThrottleLevel(), requestedLevel));
        return throttleLevel;
    }

    public void setForwardDirection(Direction direction) {
        Direction horizontal = direction.getAxis().isHorizontal() ? direction : Direction.SOUTH;
        forwardX = horizontal.getStepX();
        forwardZ = horizontal.getStepZ();
    }

    public void setShipStats(int blockCount, int coreCount, int reactorCount, int stabilizerCount) {
        this.blockCount = Math.max(1, blockCount);
        this.coreCount = Math.max(0, coreCount);
        this.reactorCount = Math.max(0, reactorCount);
        this.stabilizerCount = Math.max(0, stabilizerCount);
    }

    public void adjustShipStats(int blockDelta, int coreDelta, int reactorDelta, int stabilizerDelta) {
        blockCount = Math.max(1, blockCount + blockDelta);
        coreCount = Math.max(0, coreCount + coreDelta);
        reactorCount = Math.max(0, reactorCount + reactorDelta);
        stabilizerCount = Math.max(0, stabilizerCount + stabilizerDelta);
        if (coreCount == 0) {
            stop();
        }
    }

    public boolean hasCore() {
        return coreCount > 0;
    }

    public int getReactorCount() {
        return reactorCount;
    }

    public int getMaxThrottleLevel() {
        return Math.max(1, reactorCount);
    }

    public int getCoreCount() {
        return coreCount;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int getStabilizerCount() {
        return stabilizerCount;
    }

    public boolean hasStabilizer() {
        return stabilizerCount > 0;
    }

    public boolean isFullyStabilized() {
        return stabilizerCount * BLOCKS_PER_STABILIZER >= blockCount;
    }

    public double getSpeedMultiplier() {
        return calculateSpeedMultiplier(blockCount, throttleLevel);
    }

    public double getMaxSpeed(double baseSpeed) {
        return calculateMaxSpeed(baseSpeed, blockCount, throttleLevel);
    }

    public static double calculateSpeedMultiplier(int blockCount, int throttleLevel) {
        double sizePenalty = Math.sqrt(50.0 / Math.max(50.0, blockCount));
        return Math.max(0.15, sizePenalty * Math.max(1, throttleLevel));
    }

    public static double calculateMaxSpeed(double baseSpeed, int blockCount, int throttleLevel) {
        return baseSpeed * calculateSpeedMultiplier(blockCount, throttleLevel);
    }

    public void stop() {
        desiredFwdSpeed = 0.0;
        desiredVy       = 0.0;
        desiredYawRate  = 0.0;
        piloted = false;
    }

    @Override
    public void physTick(PhysShip physShip, PhysLevel physLevel) {
        double mass = physShip.getMass();
        Vector3dc worldVel = physShip.getVelocity();

        boolean levelHorizontally = true;
        double tvFwd  = ramp(currentFwdSpeed, piloted ? desiredFwdSpeed : 0.0, getSpeedRampStep());
        double tvY    = ramp(currentVy, piloted ? desiredVy : 0.0, getSpeedRampStep());
        double tyaw   = ramp(currentYawRate, piloted ? desiredYawRate : 0.0, getYawRampStep());
        currentFwdSpeed = tvFwd;
        currentVy = tvY;
        currentYawRate = tyaw;

        // --- Forward / lateral forces in ship-local frame ---
        // Transform world velocity into ship-local space (rotation only).
        Matrix4dc worldToShip = physShip.getWorldToShip();
        Vector3d localVel = worldToShip.transformDirection(new Vector3d(worldVel));

        // Forward follows the controller facing in ship-local X/Z, with braking on the lateral axis.
        // applyRotDependentForce applies the force in ship-body frame, so +Z is
        // always relative to the ship's current rotation regardless of yaw.
        double fxForward = forwardX;
        double fzForward = forwardZ;
        double fxLateral = fzForward;
        double fzLateral = -fxForward;
        double forwardVel = localVel.x() * fxForward + localVel.z() * fzForward;
        double lateralVel = localVel.x() * fxLateral + localVel.z() * fzLateral;
        double forwardForce = (tvFwd - forwardVel) * mass * KP_LIN;
        double lateralForce = -lateralVel * mass * KP_LIN;
        double fx = forwardForce * fxForward + lateralForce * fxLateral;
        double fz = forwardForce * fzForward + lateralForce * fzLateral;
        physShip.applyRotDependentForce(new Vector3d(fx, 0, fz));

        // --- Vertical force in world frame (gravity compensation + ascent) ---
        if (piloted || isFullyStabilized()) {
            double fy = (tvY - worldVel.y()) * mass * KP_LIN + mass * GRAVITY;
            physShip.applyInvariantForce(new Vector3d(0, fy, 0));
        }

        // --- Angular control ---
        // Y (yaw) : proportional drive toward desiredYawRate
        // X (pitch) + Z (roll) : damp to zero to keep ship upright
        Vector3dc omega = physShip.getOmega();
        Vector3d levelTorque = levelHorizontally ? getLevelingTorque(physShip, mass) : new Vector3d();
        physShip.applyInvariantTorque(new Vector3d(
                -omega.x() * mass * KP_ROT + levelTorque.x,
                (tyaw - omega.y()) * mass * KP_YAW,
                -omega.z() * mass * KP_ROT + levelTorque.z
        ));
    }

    private Vector3d getLevelingTorque(PhysShip physShip, double mass) {
        Quaterniondc rotation = physShip.getTransform().getShipToWorldRotation();
        Vector3d shipUp = rotation.transform(new Vector3d(0, 1, 0));
        Vector3d axis = shipUp.cross(new Vector3d(0, 1, 0), new Vector3d());
        return axis.mul(mass * KP_LEVEL);
    }

    private double getSpeedRampStep() {
        return Math.max(0.012, 0.028 * getSpeedMultiplier());
    }

    private double getYawRampStep() {
        return Math.max(0.004, 0.010 * getSpeedMultiplier());
    }

    private double ramp(double current, double target, double maxStep) {
        double delta = target - current;
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.signum(delta) * maxStep;
    }
}
