package cn.zgnhit.mcwuwa.client.presentation;

import org.joml.Matrix4f;

import java.util.Map;

/** View-only transform for the dedicated player arms, shared by skin and held items. */
final class FirstPersonArmView {
    private static final float RADIANS = (float) (Math.PI / 180);
    private static final float MAXIMUM_PITCH = 36;

    private FirstPersonArmView() {}

    /**
     * The mixed FP pose drives one bounded, rigid view tilt for skin and both items.
     * The maximum of the two upper-arm reaches is invariant under a handedness swap.
     */
    static Matrix4f modelToView(Map<String, ActionPresentationRegistry.Pose> pose) {
        // The 436-clip registry/matrix sweep selected this root against hand framing
        // and forearm-envelope obstruction, with the actual closed shoulder caps
        // constrained below a nominal 110-degree vertical view. Keep the .64
        // uniform scale; the view root never edits anatomical bone lengths.
        return new Matrix4f().translation(0, -.664f, -.35f)
            .rotateX((float) Math.PI + pitchDegrees(pose) * RADIANS)
            .scale(.64f).translate(0, -24f / 16, 0);
    }

    static float pitchDegrees(Map<String, ActionPresentationRegistry.Pose> pose) {
        float reach = Math.max(upperArmReach(pose.get("right_arm")), upperArmReach(pose.get("left_arm")));
        // Dedicated neutral clips begin at -18 degrees; full reaches are around
        // -110. Smoothstep keeps the neutral and saturated ends free of a kink.
        float mix = Math.clamp((reach - 18) / 92, 0, 1);
        return MAXIMUM_PITCH * mix * mix * (3 - 2 * mix);
    }

    private static float upperArmReach(ActionPresentationRegistry.Pose pose) {
        return pose == null || !Float.isFinite(pose.rotation().x) ? 0 : -pose.rotation().x;
    }

    /** Fixed direction reference. Input is block space; camera space looks along -Z. */
    static Matrix4f modelToView() {
        // The dedicated clips lift the arms towards model +Z. Turn that reach towards
        // camera -Z while preserving screen X and a positive determinant. Pivot the
        // view at shoulder height, then keep the actual shoulder caps below the frame.
        // This is one rigid view transform: no mirrored skin, shortened bones or
        // per-limb offsets, and no changes to the camera, actor or TP pose.
        return new Matrix4f().translation(0,-.50f,-.30f)
            .rotateX((float)Math.PI).scale(.64f).translate(0,-22f/16,0);
    }
}
