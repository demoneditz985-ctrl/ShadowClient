import com.project.vortex.client.constructors.CheatCategory
import com.project.vortex.client.constructors.Element
import com.project.vortex.client.constructors.GameManager
import com.project.vortex.client.game.InterceptablePacket
import com.project.vortex.client.util.AssetManager

class CmdListener(private val moduleManager: GameManager) : Element(
    name = "ChatListener",
    category = CheatCategory.Misc,
    displayNameResId = AssetManager.getString("module_chat_listener")
) {
    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {

    }
} 