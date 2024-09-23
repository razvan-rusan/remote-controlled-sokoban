package sokoban

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import processing.core.PApplet
import processing.core.PImage
import sokoban.Constants.levelgenPath
import sokoban.Constants.outputLevelsPath
import sokoban.Constants.tilesPath
import java.io.File

@Suppress("unused")
class LevelGenerator : PApplet() {

    private lateinit var levelNames: List<String>
    private lateinit var palette: PImage
    private lateinit var indexToColorDefinitions: MutableList<Int>
    private lateinit var indexToTileImageDefinitions: MutableList<PImage>
    private lateinit var colorToImage: Map<Int, PImage>
    private lateinit var colorToTileName: Map<Int, String>
    private lateinit var tileNames: List<String>
    private lateinit var tiles: MutableList<PImage>
    private lateinit var generatedLevels: Array<Level>
    private var computedPixelRatio: Int = -1
    private val levelNamingConvention = "(level[0-9]+)\\.png".toRegex()

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

    private fun PImage.average(): Int {
        var red: Int = 0
        var green: Int = 0
        var blue: Int = 0
        this.updatePixels()
        for (pixel in this.pixels) {
            red += red(pixel).toInt()
            green += green(pixel).toInt()
            blue += blue(pixel).toInt()
        }
        return color(red/pixels.size, green/pixels.size, blue/pixels.size)
    }

    private fun PImage.averageImage(): PImage {
        val avg: Int = this.average()
        for (i in 0..<this.width) {
            for (j in 0..<this.height) {
                this.set(i,j,avg)
            }
        }
        return this
    }

    override fun settings() {
        size(832, 832)
    }

    override fun setup() {
        tileNames =
            File(tilesPath)
                .walk()
                .map(File::getName)
                .drop(1)
                .toList()
        levelNames =
            File(levelgenPath)
                .walk()
                .map(File::getName)
                .drop(1)
                .filter{levelNamingConvention.matches(it)}
                .map{it.dropLast(4)} //erases ".png" from the filename
                .sortedWith { lvl_a, lvl_b ->
                    val ordinal_a: Int = lvl_a.substring(5).toInt() //e.g. erases "level" from "level10"
                    val ordinal_b: Int = lvl_b.substring(5).toInt()
                    ordinal_a.compareTo(ordinal_b)
                }
                .toList()
        palette = loadImage("${levelgenPath}/define_palette.png")
        palette.updatePixels()
        indexToColorDefinitions = palette.pixels.toMutableList()
        try {
            assert(palette.pixels.size == tileNames.size)
        } catch (e:AssertionError) {
            println("Pixel count in definition palette should be equal to tile count!")
        }
        indexToTileImageDefinitions = mutableListOf<PImage>()
        for (tile in tileNames) {
            indexToTileImageDefinitions.add(loadImage("${tilesPath}/${tile}"))
        }
        colorToImage = indexToColorDefinitions.zip(indexToTileImageDefinitions).toMap()
        colorToTileName = indexToColorDefinitions.zip(tileNames).toMap()
        generatedLevels = Array<Level>(levelNames.size){
            levelIndex ->
                val currentLevel: PImage = loadImage("${levelgenPath}/${levelNames[levelIndex]}.png")
                val currentTileMatrix: MutableList<MutableList<Int>> = mutableListOf<MutableList<Int>>()
                for (i in 0..<currentLevel.width) {
                    val tileRow: MutableList<Int> = mutableListOf()
                    for (j in 0..<currentLevel.height) {
                        tileRow.add(currentLevel.get(i,j))
                    }
                    currentTileMatrix.add(tileRow)
                }
                return@Array Level(currentTileMatrix, currentLevel.width, currentLevel.height, levelNames[levelIndex])
        }
        tiles = mutableListOf()
        for (name in tileNames) {
            tiles.add(loadImage("${tilesPath}/${name}"))
        }
        //could've omitted the this keyword
        //but it gives it more semantic clarity
        computedPixelRatio = min(this.width, this.height)/generatedLevels[0].widthInTiles/tiles[0].width
        println("There are ${generatedLevels.size} levels avilable.")
        println("tiles: ${tileNames}")
        println("levels: ${levelNames}")
        println("ratio: ${computedPixelRatio}")
    }

    private fun showLevel(which: Int) {
        val lvl: Level = generatedLevels[which]
        val lvlTileCodes: List<List<Int>> = lvl.tileColorCodes
        //yeah, let's just assume all tiles have the same width,
        //I guess
        val dx: Float = tiles[0].width.toFloat()
        val dy: Float = tiles[0].height.toFloat()
        for (i in 0..<lvl.widthInTiles) {
            for (j in 0..<lvl.heightInTiles) {
                val currentTile : PImage? = colorToImage[lvlTileCodes[i][j]]
                currentTile?.let{
                    image(it
                        //.averageImage()
                        .embiggen(computedPixelRatio), i*dx*computedPixelRatio, j*dy*computedPixelRatio)
                }
            }
        }
    }

    private fun saveGeneratedLevelsToJSON() {
        for (level in generatedLevels) {
            val outputFile: File = File("${outputLevelsPath}/${level.name}.json")
            if (!outputFile.exists()) {
                outputFile.createNewFile()
            }
            outputFile.writeText(Json.encodeToString(level))
        }

    }

    override fun draw() {
        background(100)
        showLevel(2)
        noLoop()
    }

    override fun keyPressed() {
        if (key==' ') {
            saveGeneratedLevelsToJSON()
            println("Levels made. Should be good to go!")
        }
    }
}