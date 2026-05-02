package fr.benjamin.customships.assembly;

import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
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

    private volatile double desiredFwdSpeed = 0.0;
    private volatile double desiredVy       = 0.0;
    private volatile double desiredYawRate  = 0.0;

    private static final double KP_LIN  = 5.0;   // linear proportional gain
    private static final double KP_YAW  = 15.0;  // yaw drive gain
    private static final double KP_ROT  = 20.0;  // pitch/roll damping gain
    private static final double GRAVITY = 9.8;

    public void setDesiredFwdSpeed(double v) { this.desiredFwdSpeed = v; }
    public void setDesiredVy(double v)       { this.desiredVy = v; }
    public void setDesiredYawRate(double v)  { this.desiredYawRate = v; }

    public void stop() {
        desiredFwdSpeed = 0.0;
        desiredVy       = 0.0;
        desiredYawRate  = 0.0;
    }

    @Override
    public void physTick(PhysShip physShip, PhysLevel physLevel) {
        double mass = physShip.getMass();
        Vector3dc worldVel = physShip.getVelocity();

        double tvFwd  = desiredFwdSpeed;
        double tvY    = desiredVy;
        double tyaw   = desiredYawRate;

        // --- Forward / lateral forces in ship-local frame ---
        // Transform world velocity into ship-local space (rotation only).
        Matrix4dc worldToShip = physShip.getWorldToShip();
        Vector3d localVel = worldToShip.transformDirection(new Vector3d(worldVel));

        // Forward (Z) and lateral-braking (X) in one rot-dependent force call.
        // applyRotDependentForce applies the force in ship-body frame, so +Z is
        // always the ship's current forward direction regardless of yaw.
        double fz = (tvFwd - localVel.z()) * mass * KP_LIN;
        double fx = (0     - localVel.x()) * mass * KP_LIN; // brake lateral drift
        physShip.applyRotDependentForce(new Vector3d(fx, 0, fz));

        // --- Vertical force in world frame (gravity compensation + ascent) ---
        double fy = (tvY - worldVel.y()) * mass * KP_LIN + mass * GRAVITY;
        physShip.applyInvariantForce(new Vector3d(0, fy, 0));

        // --- Angular control ---
        // Y (yaw) : proportional drive toward desiredYawRate
        // X (pitch) + Z (roll) : damp to zero to keep ship upright
        Vector3dc omega = physShip.getOmega();
        physShip.applyInvariantTorque(new Vector3d(
                -omega.x() * mass * KP_ROT,
                (tyaw - omega.y()) * mass * KP_YAW,
                -omega.z() * mass * KP_ROT
        ));
    }
}
