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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.*
import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val NotiBox = findViewById<TextView>(R.id.tv_message)      //导入对应的控件
        NotiBox.movementMethod = ScrollingMovementMethod()      //允许滑条
        val BuFindFiles = findViewById<Button>(R.id.bu_files)
        val BuMerge = findViewById<Button>(R.id.bu_merge)
        val BuCA = findViewById<Button>(R.id.bu_clearall)
        val OuNa = findViewById<TextView>(R.id.edt_output)
        val MerProg = findViewById<ProgressBar>(R.id.pB_Merge)

        val file = File(this.cacheDir, "mergelist.txt")   //在缓存目录中设置合并列表的缓存文件
        var filenum = 0

        var allframes = 0    //总帧数

        //通过registerForActivityResult方法定义一个函数处理回传数据
        val GetFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if(it.resultCode == RESULT_OK){  //即，成功实行

                val fileuri = it.data?.data
                //val context: Context = applicationContext       //问题出在content的格式上?。下载文件对应的是msf

                if(MyOwnClass.FilePositionType(fileuri!!)=="Document") {
                    val filepath = MyOwnClass.getRealPath(fileuri).toString()    //获取文件路径
                    filenum += 1

                    //创建合并列表
                    val filename: String = "file '$filepath'\n"
                    file.appendText(filename, Charsets.UTF_8)

                    //获取视频信息 [width height frames]=============
                    //wmv格式可能会出错
                    val mediaInfolog = FFprobeKit.getMediaInformation(filepath).allLogsAsString   //获取视频log
                    val mediaInfo = MyOwnClass.getVideoInfo(mediaInfolog)    //从log中获取视频信息

                    //可视化信息===================================================================
                    NotiBox.append("File $filenum: $filepath\n")
                    NotiBox.append(" ".padStart("File $filenum: ".length + 8) + "Resulotion:${mediaInfo[0]}*${mediaInfo[1]}   Frames:${mediaInfo[2]}\n")

                    //设置输出路径中显示文字===========================
                    OuNa.text = filepath.replace("\\d+.mp4".toRegex(), ".mp4")

                    //计算总帧数，为进度条做准备=======================
                    allframes += mediaInfo[2]
                }
                else{ NotiBox.append("Please choose files from document mode" + '\n') }

            }
            else{ NotiBox.append("Please choose a file" + '\n') }
        }

        BuFindFiles.setOnClickListener {
            //要想读写文件需要有读写权限
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)        //新建一个intent,打开文件管理
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "video/*"     //指定文件类型
            GetFiles.launch(intent)       //利用GetFilePath启动intent（文件管理），获得的结果会返回给GetFilePath进行处理
        }

        BuMerge.setOnClickListener {

            val inlist = file.toPath().toString()
            val outpath = OuNa.text.toString()

            val command = "-y -f concat -safe 0 -i $inlist -c copy \"$outpath\""

            var mergestate:String = ""

            if(filenum !=0 ) {
                //合并线程
                Thread {
                    val session = FFmpegKit.execute(command)
                    mergestate = session.state.toString()
                }.start()


                //计时和动画合并线程
                Thread {
                    var timec = 0.0
                    val timein = 100.0

                    val Notetem = NotiBox.text.toString()
                    val ani = arrayOf(".","..","...","....",".....")

                    while (mergestate != "COMPLETED") {//merth.state.toString()!= "COMPLETED"
                        Thread.sleep(timein.toLong())
                        timec += timein / 1000
                        BuMerge.text = "Processing: ${String.format("%.1f", timec).toString()}s"

                        NotiBox.text = Notetem
                        NotiBox.append("Processing${ani[(timec*2).toInt()%(ani.size)]}")   //文本框中的跟新进度是1000/2ms
                    }
                    BuMerge.text = "Finished!! Total Time: ${String.format("%.1f", timec).toString()}s"
                    NotiBox.text = Notetem
                    NotiBox.append(">>>>$mergestate\n")
                    NotiBox.append(">>>>Total Time : ${String.format("%.1f", timec).toString()}s\n")
                }.start()


            }else{NotiBox.append("You don't choose any file\n")}
        }

        BuCA.setOnClickListener {
            file.delete()
            OuNa.text = "output file name"
            //NotiBox.text = ""
            NotiBox.append("All Cleared\n")
            filenum = 0
            BuMerge.text = "Merge"

        }

        //TextView自动滚动线程，监听NotiBox，超过高度就滑动
        NotiBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                // This method is called after the text is changed
                //超过长度就滑动
                val scrollAmount: Int = NotiBox.layout.getLineTop(NotiBox.lineCount) - NotiBox.height
                if (scrollAmount > 0) NotiBox.scrollTo(0, scrollAmount) else NotiBox.scrollTo(0, 0)
            }
        })


    }



}