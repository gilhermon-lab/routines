package com.gil.routines.widget

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gil.routines.data.ModeStore
import com.gil.routines.engine.ModeApplier
import com.gil.routines.engine.RoutineEngine

/**
 * אריח בהגדרות המהירות — נגיש בגלילה מלמעלה, בלי לצאת מהמסך הנוכחי.
 * המשתמש מוסיף אותו פעם אחת דרך עריכת האריחים.
 */
class MeetingTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        sync()
    }

    override fun onClick() {
        super.onClick()
        val live = RoutineEngine.activeModes(this).any { it.id == ModeWidget.TARGET_MODE }
        ModeStore.update(this, ModeWidget.TARGET_MODE) {
            it.copy(manualOverride = !live, enabled = true)
        }
        ModeApplier.applyCurrentState(this)
        ModeWidget.refreshAll(this)
        sync()
    }

    private fun sync() {
        val mode = ModeStore.load(this).find { it.id == ModeWidget.TARGET_MODE }
        val live = RoutineEngine.activeModes(this).any { it.id == ModeWidget.TARGET_MODE }
        qsTile?.apply {
            label = mode?.name ?: "ישיבה"
            state = if (live) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
