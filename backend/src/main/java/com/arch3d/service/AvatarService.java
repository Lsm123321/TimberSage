package com.arch3d.service;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarService {
    /**
     * 上传头像并返回可直接放进 img src 的地址（形如 /minio/avatars/xxx.png）。
     * 存相对路径是为了 dev（Vite 代理 /minio）和生产（Nginx 代理 /minio）都能取到图。
     */
    String uploadAvatar(MultipartFile file);

    /** 删除头像对象；传 null 或空串直接忽略，不报错 */
    void deleteAvatar(String avatarUrl);
}
