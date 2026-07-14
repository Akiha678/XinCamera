```
PhotonCamera
│
├── Camera Engine（Kotlin）
│
├── Native Engine（C++）
│
├── Render Engine（OpenGL ES）
│
├── UI（Compose）
│
└── Storage
```


```
PhotonCamera
│
├── app
│
├── camera          // 相机控制
│
├── image           // 图片处理（JNI + C++）
│
├── renderer        // 预留(OpenGL)
│
├── data            // 数据
│
├── ui              // Compose页面
│
├── common          // 工具
│
└── docs            // 文档
```

# 第一版

## 第一部分：实时预览
1. 切换前后摄像头
2. 放大缩小
3. 点击对焦
4. 开关闪光灯


## 第二部分：专业模式
1. ISO调节
2. 快门速度调节
3. 白平衡调节

## 第三部分：直方图
- C++实现

## 第四部分：灰度
- C++实现

## 第五部分：旋转图片
- C++实现

## 第六部分：裁剪图片

## 第七部分：镜像

## 第八部分：模糊

## 第九部分：锐化

## 第十部分：边缘检测