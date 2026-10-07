package com.project.vortex.client.game.event

import com.project.vortex.client.constructors.NetBound
import com.project.vortex.client.game.inventory.AbstractInventory

class EventInventorySlotUpdate(
    session: NetBound,
    val inventory: AbstractInventory,
    val slot: Int
) : GameEvent(session, "InventorySlotUpdate")
