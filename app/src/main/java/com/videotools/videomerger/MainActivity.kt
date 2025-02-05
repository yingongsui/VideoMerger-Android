package com.videotools.videomerger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader


class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val notiBox = findViewById<TextView>(R.id.tv_message)      //导入对应的控件
        notiBox.movementMethod = ScrollingMovementMethod()      //允许滑条
        val buFindFiles = findViewById<Button>(R.id.bu_files)   //File Button
        val buMerge = findViewById<Button>(R.id.bu_merge)       //Merge Button
        val buCA = findViewById<Button>(R.id.bu_clearall)       //Clear Button
        val teOutName = findViewById<TextView>(R.id.edt_output)      //Output Text Editor
        val proProg = findViewById<ProgressBar>(R.id.pB_Merge)  //Progressbar
        val lvFileList = findViewById<ListView>(R.id.FilelistView)    //File ListView

        val buTest = findViewById<Button>(R.id.bu_test)

        var currentstate = "Idle"



        //通过registerForActivityResult方法定义一个函数处理回传数据
        val getFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            val filelist = MyOwnClass.allfileinlist(lvFileList)   //记录文件
            var filenum = filelist.size

            if(it.resultCode == RESULT_OK){  //即，成功实行

                val fileuri = it.data?.data
                //val context: Context = applicationContext       //问题出在content的格式上?。下载文件对应的是msf
                filenum += 1

                if(MyOwnClass.filePositionType(fileuri!!)=="Document") {
                    val filepath = MyOwnClass.getRealPath(fileuri).toString()    //获取文件路径
                    //获取视频信息 [width height frames]=============
                    //wmv格式可能会出错
                    val mediaInfolog = FFprobeKit.getMediaInformation(filepath).allLogsAsString   //获取视频log
                    //println(mediaInfolog)
                    val mediaInfo = MyOwnClass.getVideoInfo(mediaInfolog)    //从log中获取视频信息
                    //println(mediaInfo[3])

                    //可视化信息===================================================================
                    filelist.add(mutableMapOf("filepath" to ("$filenum. $filepath"),
                        "filedet" to ( "Resulotion: ${mediaInfo[0]}*${mediaInfo[1]}, Frames: ${mediaInfo[2]}, SRates: ${mediaInfo[3]}")))

                    //设置输出路径中显示文字===========================
                    teOutName.text = filepath.replace("\\d+.mp4".toRegex(), ".mp4")

                }
                else{ notiBox.append("Please choose files from document mode!" + '\n') }

            }
            else{ notiBox.append("Please choose a file!" + '\n') }

            lvFileList.adapter = SimpleAdapter(
                this,
                filelist,
                android.R.layout.simple_list_item_2,
                arrayOf("filepath" , "filedet"),
                intArrayOf(android.R.id.text1, android.R.id.text2)
            )

        }


        //按钮行为
        buFindFiles.setOnClickListener {
            //要想读写文件需要有读写权限
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)        //新建一个intent,打开文件管理
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "video/*"     //指定文件类型
            getFiles.launch(intent)       //利用GetFilePath启动intent（文件管理），获得的结果会返回给GetFilePath进行处理
        }

        buMerge.setOnClickListener {

            if(currentstate == "Idle") {
                val data = MyOwnClass.allfileinlist(lvFileList)
                //记录总帧数
                if (data.size != 0) {
                    currentstate = "Processing"
                    notiBox.append("\n=================================\n")
                    var allframes = 0
                    val tem = MyOwnClass.filelisttreat(data, "Frames")    //从listview中获取文件列表 以及 去除文件列表中的编号信息以及非帧数信息

                    //创建合并列表
                    val mergerlistFile = File(this.cacheDir, "mergelist.txt")   //在缓存目录中设置合并列表的缓存文件
                    for (i in 0 until tem.size) {
                        allframes += tem[i]["filedet"].toString().toInt()
                        val filepath = tem[i]["filepath"]
                        mergerlistFile.appendText("file '$filepath'\n", Charsets.UTF_8)
                    }

                    notiBox.append("Total Frames:%d\n".format(allframes))
                    proProg.max = allframes

                    val inlist = mergerlistFile.toPath().toString()
                    val outpath = teOutName.text.toString()
                    val command = "-y -f concat -safe 0 -i $inlist -c copy \"$outpath\""

                    //合并线程
                    val mergrthread = Thread {
                        val session = FFmpegKit.execute(command)
                        currentstate  = session.state.toString()
                    }

                    //进度获取线程
                    val getprothread = Thread {
                        Runtime.getRuntime().exec("logcat -c")              //清除既存日志
                        val process = Runtime.getRuntime().exec("logcat")   //开始记录日志
                        val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                        //用BufferedReader读取的是所有存在的日志，而非当前进程开始发生的日志。因此前面的日志记录会有残留
                        //并且用BufferedReader读取时，当前进程只能读1行，之后就结束了。
                        var line: String?
                        while ((bufferedReader.readLine().also { line = it }) != null) {
                            if (!line.toString().contains("System.out:") && line.toString().contains("ffmpeg-kit: frame=")) {
                                //正常运行，获取帧率
                                val currentframe = Regex("frame=\\d+").find(line.toString().replace(" ", ""))?.value?.replace("frame=", "")?.toInt()
                                if (currentframe != null) {
                                    //notiBox.append("Current Frame:%d".format(currentframe) + "\n")
                                    proProg.progress = currentframe
                                }
                            } else if (line.toString().contains(" E ") && line.toString().contains("ffmpeg-kit")) {
                                //ffmpeg发生错误
                                notiBox.append(line.toString() + "\n")
                                currentstate = "Idle"
                                break
                            }

                            if (currentstate  == "COMPLETED") { break }
                        }
                        Runtime.getRuntime().exec("logcat -c")              //清除既存日志

                    }

                    //计时
                    val timerthread = Thread {
                        var timec = 0.0
                        val timein = 100.0

                        //val notiTem = notiBox.text.toString()
                        //val ani = arrayOf(".","..","...","....",".....")

                        while (currentstate  != "COMPLETED") {//merth.state.toString()!= "COMPLETED"
                            Thread.sleep(timein.toLong())
                            timec += timein / 1000
                            buMerge.text = getString(R.string.cost_time, timec)
                            //notiBox.text = notiTem
                            //notiBox.append(getString(R.string.processing,ani[(timec*2).toInt()%(ani.size)]))   //文本框中的跟新进度是1000/2ms
                        }
                        buMerge.text = getString(R.string.cost_time, timec)
                        //notiBox.text = notiTem
                        notiBox.append("$currentstate!\n")
                        notiBox.append(getString(R.string.total_time, timec) + "\n")
                        mergerlistFile.delete()
                        currentstate = "Idle"
                    }

                    mergrthread.start()
                    getprothread.start()
                    timerthread.start()


                } else {
                    notiBox.append(getString(R.string.no_file) + "\n")
                }
            }
            else{
                notiBox.append("$currentstate...\n")
            }

        }

        buCA.setOnClickListener {
            teOutName.text = getString(R.string.output_name)
            notiBox.append(getString(R.string.all_cleared) + "\n")
            buMerge.text = getResources().getStringArray(R.array.merge_state)[0]
            lvFileList.adapter = null
            currentstate = "Idle"
            proProg.progress = 0

        }

        buTest.setOnClickListener {
            //测试按钮
//            println(tem::class.java.typeName)
            notiBox.append(lvFileList.adapter.toString() + "\n")
//            for(i in 0 until lvFileList.adapter.count){
//                val tem = lvFileList.getItemAtPosition(i) as MutableMap <*,*>
//                println(tem["filepath"].toString().replaceBefore("/",""))
//                println(tem["filedet"].toString().split(",")[1].filter { it.isDigit() })
//            }
//            var tem2 = MyOwnClass.allfileinlist(lvFileList)
//            println("Test" + tem2)
//            println("Test" + tem2::class.qualifiedName)
//            println("Test" + MyOwnClass.filelisttreat(tem2,"NoNum"))
//            println("Test" + MyOwnClass.filelisttreat(tem2,"AddNum"))
//            println("Test" + MyOwnClass.filelisttreat(tem2,"Frames"))
//            println("Test" + MyOwnClass.filelisttreat(tem2,"Remove",0))


        }

        //列表长按设置
        lvFileList.setOnItemLongClickListener { _, _, pos, _ -> // ->操作，意思是长按item会产生4个参数，将这四个参数传递给后面额函数
            var tem = MyOwnClass.allfileinlist(lvFileList)
            notiBox.append("File %s deleted!\n".format(tem[pos]["filepath"]))
            tem = MyOwnClass.filelisttreat(tem,"NoNum")
            tem = MyOwnClass.filelisttreat(tem,"Remove",pos)
            tem = MyOwnClass.filelisttreat(tem,"AddNum")
            lvFileList.adapter = SimpleAdapter(
                this,
                tem,
                android.R.layout.simple_list_item_2,
                arrayOf("filepath" , "filedet"),
                intArrayOf(android.R.id.text1, android.R.id.text2)
            )
            //lvFileList.getItemAtPosition(i)
            true
        }


        //TextView自动滚动线程，监听notiBox，超过高度就滑动
        notiBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                // This method is called after the text is changed
                //超过长度就滑动
                val scrollAmount: Int = notiBox.layout.getLineTop(notiBox.lineCount) - notiBox.height
                if (scrollAmount > 0) notiBox.scrollTo(0, scrollAmount) else notiBox.scrollTo(0, 0)
            }
        })


    }



}