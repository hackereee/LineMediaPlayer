#一个简单的本地代理播放器
*	[介绍](#1)
*	[使用方法](#2)


----------
##	 <span id = "1">介绍</span>
>     由于android原本的播放器MediaPlayer并不支持音视频缓存控制，而且MediaPlayer 中无法获取到流媒体数据，但是我恰好需要缓存这个流媒体到本地，并且第二次打开此媒体的时候直接读取缓存呢？所以这里我参考了网上很多的解决方案，使用ServerSocket 进行本地代理，播放的时候让播放器发起一个本地的媒体源获取，然后通过代理我们取到此client请求，并且重新组装以后发送远程服务器请求真正的流媒体数据，这时候我们就可以取到真正的数据并且进行缓存，以便第二次使用的时候直接读取缓存。
>     模拟的时候进行了 302 重定向处理，在处理的时候没有什么问题。

##	<span id = "2">使用方法</span>
>    建议在Application 处使用此方法进行初始化，这里会初始化一个文件夹作为缓存路径：
>    `SocketProxyPlay.getInstance().init(this, true);`
>    其中，第一个参数为上下文，第二个参数为是否开启本地监听，如果这里为false，那么在需要使用的时候需要调用`SocketProxyPlay.getInstance().listening()`方进行监听开启，此方法建议和`close()`方进行对应使用，一般在Activity的onCreate 开启监听， 在 onDestory 进行close关闭监听。
>    如果在Android 6.0 之上操作系统中，使用sd权限，需要进行权限申请，建议开启一个过渡的Activity（比如欢迎页）进行权限申请，申请结束后在申请结束的回调` onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) `里使用 `SocketProxyPlay.getInstance().createDefaultSavePath(Context context)`方法进行再次的缓存路径确认，以确保路径真的是sd卡路径，以免占用太多手机存储空间。
>    具体可以参考DemoActivity 里的写法进行使用。
=======
# LineMediaPlayer
