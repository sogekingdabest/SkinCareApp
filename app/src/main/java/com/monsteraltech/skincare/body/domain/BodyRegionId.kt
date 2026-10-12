package com.monsteraltech.skincare.body.domain

/** Persisted keys are independent of labels, palette and the body-map asset. */
enum class BodyRegionId(val value: String) {
    HEAD("head"), RIGHT_ARM("right_arm"), TORSO("torso"), LEFT_ARM("left_arm"),
    RIGHT_LEG("right_leg"), LEFT_LEG("left_leg"), UNKNOWN("unknown");

    companion object {
        fun fromValue(value: String?): BodyRegionId? = entries.firstOrNull { it.value == value }
    }
}
