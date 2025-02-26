package com.brentvatne.common.api

import android.os.Parcel
import android.os.Parcelable
import com.brentvatne.common.toolbox.ReactBridgeUtils
import com.facebook.react.bridge.ReadableMap

class ControlsConfig : Parcelable {
    var hideSeekBar: Boolean = false
    var hideDuration: Boolean = false
    var hidePosition: Boolean = false
    var hidePlayPause: Boolean = false
    var hideForward: Boolean = false
    var hideRewind: Boolean = false
    var hideNext: Boolean = false
    var hidePrevious: Boolean = false
    var hideFullscreen: Boolean = false
    var hideNavigationBarOnFullScreenMode: Boolean = true
    var hideNotificationBarOnFullScreenMode: Boolean = true
    var liveLabel: String? = null
    var hideSettingButton: Boolean = true
    var preferredOrientation: String? = "sensor"

    var seekIncrementMS: Int = 10000

    constructor()

    private constructor(parcel: Parcel) {
        hideSeekBar = parcel.readByte() != 0.toByte()
        hideDuration = parcel.readByte() != 0.toByte()
        hidePosition = parcel.readByte() != 0.toByte()
        hidePlayPause = parcel.readByte() != 0.toByte()
        hideForward = parcel.readByte() != 0.toByte()
        hideRewind = parcel.readByte() != 0.toByte()
        hideNext = parcel.readByte() != 0.toByte()
        hidePrevious = parcel.readByte() != 0.toByte()
        hideFullscreen = parcel.readByte() != 0.toByte()
        hideNavigationBarOnFullScreenMode = parcel.readByte() != 0.toByte()
        hideNotificationBarOnFullScreenMode = parcel.readByte() != 0.toByte()
        liveLabel = parcel.readString()
        hideSettingButton = parcel.readByte() != 0.toByte()
        preferredOrientation = parcel.readString()
        seekIncrementMS = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (hideSeekBar) 1 else 0)
        parcel.writeByte(if (hideDuration) 1 else 0)
        parcel.writeByte(if (hidePosition) 1 else 0)
        parcel.writeByte(if (hidePlayPause) 1 else 0)
        parcel.writeByte(if (hideForward) 1 else 0)
        parcel.writeByte(if (hideRewind) 1 else 0)
        parcel.writeByte(if (hideNext) 1 else 0)
        parcel.writeByte(if (hidePrevious) 1 else 0)
        parcel.writeByte(if (hideFullscreen) 1 else 0)
        parcel.writeByte(if (hideNavigationBarOnFullScreenMode) 1 else 0)
        parcel.writeByte(if (hideNotificationBarOnFullScreenMode) 1 else 0)
        parcel.writeString(liveLabel)
        parcel.writeByte(if (hideSettingButton) 1 else 0)
        parcel.writeString(preferredOrientation)
        parcel.writeInt(seekIncrementMS)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ControlsConfig> {
        @JvmStatic
        fun parse(controlsConfig: ReadableMap?): ControlsConfig {
            val config = ControlsConfig()

            if (controlsConfig != null) {
                config.hideSeekBar = ReactBridgeUtils.safeGetBool(controlsConfig, "hideSeekBar", false)
                config.hideDuration = ReactBridgeUtils.safeGetBool(controlsConfig, "hideDuration", false)
                config.hidePosition = ReactBridgeUtils.safeGetBool(controlsConfig, "hidePosition", false)
                config.hidePlayPause = ReactBridgeUtils.safeGetBool(controlsConfig, "hidePlayPause", false)
                config.hideForward = ReactBridgeUtils.safeGetBool(controlsConfig, "hideForward", false)
                config.hideRewind = ReactBridgeUtils.safeGetBool(controlsConfig, "hideRewind", false)
                config.hideNext = ReactBridgeUtils.safeGetBool(controlsConfig, "hideNext", false)
                config.hidePrevious = ReactBridgeUtils.safeGetBool(controlsConfig, "hidePrevious", false)
                config.hideFullscreen = ReactBridgeUtils.safeGetBool(controlsConfig, "hideFullscreen", false)
                config.seekIncrementMS = ReactBridgeUtils.safeGetInt(controlsConfig, "seekIncrementMS", 10000)
                config.hideNavigationBarOnFullScreenMode = ReactBridgeUtils.safeGetBool(controlsConfig, "hideNavigationBarOnFullScreenMode", true)
                config.hideNotificationBarOnFullScreenMode = ReactBridgeUtils.safeGetBool(controlsConfig, "hideNotificationBarOnFullScreenMode", true)
                config.liveLabel = ReactBridgeUtils.safeGetString(controlsConfig, "liveLabel", null)
                config.hideSettingButton = ReactBridgeUtils.safeGetBool(controlsConfig, "hideSettingButton", true)
                config.preferredOrientation = ReactBridgeUtils.safeGetString(controlsConfig, "preferredOrientation", "sensor")
            }
            return config
        }

        override fun createFromParcel(parcel: Parcel): ControlsConfig {
            return ControlsConfig(parcel)
        }

        override fun newArray(size: Int): Array<ControlsConfig?> {
            return arrayOfNulls(size)
        }
    }
}
