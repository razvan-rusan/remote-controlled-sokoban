package sokoban

//import kotlinx.serialization.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import processing.core.PApplet
import processing.core.PGraphics
import processing.core.PImage
import processing.core.PVector
import sokoban.Constants.outputLevelsPath
import sokoban.Constants.overlaysPath
import java.io.File
import kotlin.math.abs
import io.ktor.utils.io.*

typealias Color = Int
const val EPSILON = 100 * Float.MIN_VALUE

fun approxEqual(a: Float, b:Float) = abs(a-b)<EPSILON

fun String.pluckNumberOut(): Int {
    val numPat= "[0-9]+".toRegex()
    return numPat.find(this)?.value!!.toInt()
}

@Suppress("unused")
class Sokoban : PApplet() {
    private val mutex = Object()

    private lateinit var levels: ArrayList<Level>
    private lateinit var indexToColorDefinitions: MutableList<Color>
    private lateinit var indexToTileImageDefinitions: MutableList<PImage>
    private lateinit var colorToImage: Map<Color, PImage>
    private lateinit var palette: PImage
    private lateinit var tileNames: List<String>
    private lateinit var tiles: MutableList<PImage>
    private lateinit var player: MovableEntity
    private lateinit var walls: Array<Array<Boolean>>
    private lateinit var crates: ArrayList<MovableEntity>
    private lateinit var logs: Array<Array<Boolean>>
    private lateinit var levelClearedImage: PImage
    private var firstTimeShowingLevel: Boolean = true
    private var rockTileIndex: Int = -1
    private var spawnTileIndex: Int = -1
    private var crateTileIndex: Int = -1
    private var groundTileIndex: Int = -1
    private var woodTileIndex: Int = -1
    private var tileWidth: Float = -1.0f
    private var tileHeight: Float = -1.0f
    private var computedPixelRatio: Int = -1
    private lateinit var bigCrateImage: PImage
    private lateinit var screen: PGraphics
    private var computedBigCrate = false

    private enum class scaleBy {
        width,
        height
    }

    private fun displayCrates(showOn: PGraphics) {
        for (crate in crates) {
            crate.show(showOn)
        }
    }

    private fun checkIsLevelSolved(): Boolean {
        for (crate in crates) {
            if (!logs[crate.position.x.toInt()][crate.position.y.toInt()]) return false
        }
        return true
    }

    private fun showLevel(level: Level, showOn: PGraphics?=null) {
        val levelTileCodes: List<List<Int>> = level.tileColorCodes
        //yeah, let's just assume all tiles have the same width,
        //I guess
        if (firstTimeShowingLevel) {
            walls = Array(level.widthInTiles) { Array(level.heightInTiles) { false } }
            logs = Array(level.widthInTiles) { Array(level.heightInTiles) { false } }
        }
        //look  at this wacky and uncharacteristic initialization!
        for (i in 0..<level.widthInTiles) {
            for (j in 0..<level.heightInTiles) {
                if (firstTimeShowingLevel) {
                    if (indexToColorDefinitions[rockTileIndex]==levelTileCodes[i][j]) {
                        walls[i][j] = true
                    }
                    if (indexToColorDefinitions[woodTileIndex]==levelTileCodes[i][j]) {
                        logs[i][j] = true
                    }
                    if (indexToColorDefinitions[spawnTileIndex]==levelTileCodes[i][j]) {
                        player.position = PVector(i.toFloat(), j.toFloat())
                    }
                    if (indexToColorDefinitions[crateTileIndex]==levelTileCodes[i][j]) {
                        crates.add(object : MovableEntity(
                            look = colorToImage[indexToColorDefinitions[crateTileIndex]]!!,
                            position = PVector(i.toFloat(), j.toFloat())
                        )
                        {
                            override fun show(showOn: PGraphics) {
                                if (!computedBigCrate) {
                                    computedBigCrate = true
                                    bigCrateImage = this.look.embiggen(computedPixelRatio)
                                }
                                showOn.image(bigCrateImage, this.position.x.adjust(scaleBy.width), this.position.y.adjust(scaleBy.height))
                            }
                            override fun checkBounds(byv: PVector): Boolean {
                                fun checkCollidesWithWallsIfAble(): Boolean {
                                    val ii: Int = (this.position.x+byv.x).toInt()
                                    val jj: Int = (this.position.y+byv.y).toInt()
                                    return if ((ii>=0) and (jj>=0) and (ii<levels[0].widthInTiles) and (jj<levels[0].heightInTiles)) walls[ii][jj] else false
                                }
                                fun checkHitsOtherCrates(): Boolean {
                                    for (crate in crates) {
                                        if (crate === this) continue
                                        if (approxEqual(this.position.x+byv.x, crate.position.x) and
                                            approxEqual(this.position.y+byv.y, crate.position.y)) {
                                            return true
                                        }
                                    }
                                    return false
                                }
                                return when {
                                    checkHitsOtherCrates() -> false
                                    checkCollidesWithWallsIfAble() -> false
                                    !((this.position.x + byv.x).adjust(scaleBy.width) < this@Sokoban.width.toFloat()) -> false
                                    !((this.position.y + byv.y).adjust(scaleBy.height) < this@Sokoban.height.toFloat()) -> false
                                    !((this.position.x + byv.x).adjust(scaleBy.width) >= 0.0f) -> false
                                    !((this.position.y + byv.y).adjust(scaleBy.height) >= 0.0f) -> false
                                    else -> true
                                }
                            }
                        }
                        )
                    }
                }
                var currentTile: PImage?
                currentTile = if (indexToColorDefinitions[crateTileIndex]!=levelTileCodes[i][j]) {
                    colorToImage[levelTileCodes[i][j]]
                } else {
                    colorToImage[indexToColorDefinitions[groundTileIndex]]
                }
                currentTile?.let{
                    if (showOn == null) image(it
                        .embiggen(computedPixelRatio),
                        i.adjust(scaleBy.width),
                        j.adjust(scaleBy.height))
                    else showOn.image(it
                        .embiggen(computedPixelRatio),
                        i.adjust(scaleBy.width),
                        j.adjust(scaleBy.height))
                }
            }
        }
        if (firstTimeShowingLevel) {
            firstTimeShowingLevel=false
        }
    }

    private fun PImage.embiggen(factor: Int): PImage {
        val result: PImage = createImage(this.width*factor, this.height*factor, this.format)
        result.updatePixels()
        this.updatePixels()
        for (x in 0..<this.width*factor) {
            for (y in 0..<this.height*factor) {
                result.pixels[x+y*result.width] = this.pixels[x/factor+(y/factor)*this.width]
            }
        }
        this.loadPixels()
        result.loadPixels()
        return result
    }

    private fun Float.adjust(how: scaleBy): Float {
        return when {
            (how == scaleBy.width) -> this * tileWidth * computedPixelRatio
            (how == scaleBy.height) -> this * tileHeight * computedPixelRatio
            else -> throw IllegalArgumentException()
        }
    }

    private fun Int.adjust(how: scaleBy): Float {
        return this.toFloat().adjust(how)
    }

    override fun settings() {
        size(832*3/4, 832*3/4)
    }

    override fun setup() {
        surface.setTitle("Sokoban Game")
        CoroutineScope(networkDispatcher).launch {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = aSocket(selectorManager).tcp().bind("192.168.0.31", 25565)
            kotlin.io.println("Server is listening at ${serverSocket.localAddress}")
            while (true) {
                val socket = serverSocket.accept()
                kotlin.io.println("Accepted $socket")
                launch {
                    val receiveChannel = socket.openReadChannel()
                    //val sendChannel = socket.openWriteChannel(autoFlush = true)
                    //sendChannel.writeStringUtf8("Please enter your name\n")
                    try {
                        while (true) {
                            val message = receiveChannel.readUTF8Line() ?: continue
                            kotlin.io.println("$message\n")
                            synchronized(mutex) {
                                when {
                                    (message == "w") -> player.move(PVector(0.0f, -1.0f))
                                    (message == "s") -> player.move(PVector(0.0f, 1.0f))
                                    (message == "a") -> player.move(PVector(-1.0f, 0.0f))
                                    (message == "d") -> player.move(PVector(1.0f, 0.0f))
                                    else -> {}
                                }
                            }
//                            synchronized(mutex) {
//                                email = message
//                                hasChanged = true
//                            }
                            //sendChannel.writeStringUtf8("Hello, $name!\n")
                        }
                    } catch (e: Throwable) {
                        socket.close()
                    }
                }
            }
        }
        //surface.setResizable(true)
        crates = ArrayList<MovableEntity>()
        levels = ArrayList<Level>()
        for (levelFilename: String in File(outputLevelsPath)
            .walk()
            .map(File::getName)
            .drop(1)
            .sortedWith { lvl_a, lvl_b ->
                val ordinal_a: Int = lvl_a.pluckNumberOut()
                val ordinal_b: Int = lvl_b.pluckNumberOut()
                ordinal_a.compareTo(ordinal_b)
            }
            .toList()) {
            levels.add(Json.decodeFromString<Level>(File("${outputLevelsPath}/${levelFilename}").readText()))
        }
        tileNames =
            File(Constants.tilesPath)
                .walk()
                .map(File::getName)
                .drop(1)
                .toList()
        tiles = mutableListOf()
        for (i in tileNames.indices) {
            if (tileNames[i] == "rock.png") rockTileIndex = i
            if (tileNames[i] == "spawn.png") spawnTileIndex = i
            if (tileNames[i] == "crate.png") crateTileIndex = i
            if (tileNames[i] == "ground.png") groundTileIndex = i
            if (tileNames[i] == "wood.png") woodTileIndex = i
            tiles.add(loadImage("${Constants.tilesPath}/${tileNames[i]}"))
        }

        tileWidth = tiles[0].width.toFloat()
        tileHeight = tiles[0].height.toFloat()
        palette = loadImage("${Constants.levelgenPath}/define_palette.png")
        palette.updatePixels()
        indexToColorDefinitions = palette.pixels.toMutableList()
        indexToTileImageDefinitions = mutableListOf<PImage>()
        for (tile in tileNames) {
            indexToTileImageDefinitions.add(loadImage("${Constants.tilesPath}/${tile}"))
        }
        colorToImage = indexToColorDefinitions.zip(indexToTileImageDefinitions).toMap()
        computedPixelRatio = min(this.width, this.height)/levels[0].widthInTiles/tiles[0].width
        player = object : MovableEntity(look = loadImage("${overlaysPath}/player.png").embiggen(computedPixelRatio),
            position = PVector(0.0f,0.0f)) {
            override fun show(showOn: PGraphics) {
                pushMatrix()
                translate(tileWidth/2, tileHeight/2)
                showOn.image(this.look, this.position.x.adjust(scaleBy.width), this.position.y.adjust(scaleBy.height))
                popMatrix()
            }

            override fun checkBounds(byv: PVector): Boolean {
                val scaledTotalX: Float = (this.position.x + byv.x).adjust(scaleBy.width)
                val scaledTotalY: Float = (this.position.y + byv.y).adjust(scaleBy.height)

                for (crate in crates) {
                    if (approxEqual(this.position.x+byv.x, crate.position.x) and
                        approxEqual(this.position.y+byv.y, crate.position.y)) {
                        return crate.move(byv)
                    }
                }

                fun checkCollidesWithWallsIfAble(): Boolean {
                    val i: Int = (this.position.x+byv.x).toInt()
                    val j: Int = (this.position.y+byv.y).toInt()
                    return if ((i>=0) and (j>=0) and (i<levels[0].widthInTiles) and (j<levels[0].heightInTiles)) walls[i][j] else false
                }
                return when {
                    checkCollidesWithWallsIfAble() -> false
                    !(scaledTotalX < this@Sokoban.width.toFloat()) -> false
                    !(scaledTotalY < this@Sokoban.height.toFloat()) -> false
                    !(scaledTotalX >= 0.0f) -> false
                    !(scaledTotalY >= 0.0f) -> false
                    else -> true
                }
            }
        }
        levelClearedImage = loadImage("${Constants.textPath}/level_cleared.png").embiggen(computedPixelRatio)
    }

    override fun draw() {
        screen = createGraphics(width, height)
        screen.beginDraw()
        showLevel(levels[4], screen)
        displayCrates(screen)
        player.show(screen)
        if (checkIsLevelSolved()){
            screen.image(levelClearedImage, (width/2-levelClearedImage.width/2).toFloat(),
                (height/2-levelClearedImage.height/2).toFloat()
            )
        }
        screen.endDraw()
        image(screen, 0.0f,0.0f, width.toFloat(), height.toFloat())

    }

    override fun keyPressed() {
            if (key.code == CODED) {
                when {
                    (keyCode == UP) -> player.move(PVector(0.0f, -1.0f))
                    (keyCode == DOWN) -> player.move(PVector(0.0f, 1.0f))
                    (keyCode == LEFT) -> player.move(PVector(-1.0f, 0.0f))
                    (keyCode == RIGHT) -> player.move(PVector(1.0f, 0.0f))
                }
            } else {
                when {
                    (key == 'w') -> player.move(PVector(0.0f, -1.0f))
                    (key == 's') -> player.move(PVector(0.0f, 1.0f))
                    (key == 'a') -> player.move(PVector(-1.0f, 0.0f))
                    (key == 'd') -> player.move(PVector(1.0f, 0.0f))
                }
            }

    }
}