import com.project.vortex.client.constructors.CheatCategory
import com.project.vortex.client.constructors.Element
import com.project.vortex.client.game.InterceptablePacket
import com.project.vortex.client.util.AssetManager

class ConfigManagerElement : Element(
    name = "config_manager",
    category = CheatCategory.Config,
    displayNameResId = AssetManager.getString("module_config_manager")
) {
    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {

    }
} 