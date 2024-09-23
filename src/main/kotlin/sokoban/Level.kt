package sokoban
import kotlinx.serialization.Serializable

@Serializable
data class Level(val tileColorCodes: List<List<Int>>,
                 val widthInTiles: Int,
                 val heightInTiles: Int,
                 val name: String)