import request from './request'
import type { MarkdownPreview, PageResult, UserUploadDetail, UserUploadItem } from '@/types'

// 通用图片上传（登录可用）：编辑器插图用，返回 MinIO URL
export async function uploadImageApi(file: File, dir = 'image'): Promise<{ url: string }> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('dir', dir)
  return request.post('/upload/image', formData)
}

// 普通用户：上传 / 我的上传
export async function createUserUploadApi(formData: FormData): Promise<UserUploadItem> {
  // 投稿会下载并压缩正文里的图片，耗时较长，单独放宽超时（默认全局 15s）
  return request.post('/user/uploads', formData, { timeout: 120000 })
}

export async function getMyUploadsApi(params: { page?: number; size?: number } = {}): Promise<PageResult<UserUploadItem>> {
  return request.get('/user/uploads', { params })
}

export async function getMyUploadDetailApi(id: number): Promise<UserUploadDetail> {
  return request.get(`/user/uploads/${id}`)
}

/** 投稿前的 Markdown 预览：服务端按正文同一套规则渲染 HTML + 目录 */
export async function previewUploadMdApi(contentMd: string): Promise<MarkdownPreview> {
  // 失败时前端会退回本地渲染，这里不弹全局错误提示
  return request.post('/user/uploads/preview', { contentMd }, { skipErrorToast: true, timeout: 30000 })
}

export async function deleteMyUploadApi(id: number): Promise<void> {
  return request.delete(`/user/uploads/${id}`)
}

// 管理员：查看 / 回复
export async function getAdminUploadsApi(params: {
  keyword?: string
  status?: number
  page?: number
  size?: number
}): Promise<PageResult<UserUploadItem>> {
  return request.get('/admin/uploads', { params })
}

export async function getAdminUploadDetailApi(id: number): Promise<UserUploadDetail> {
  return request.get(`/admin/uploads/${id}`)
}

export async function replyAdminUploadApi(id: number, content: string): Promise<UserUploadDetail> {
  return request.put(`/admin/uploads/${id}/reply`, { content })
}

export async function deleteAdminUploadApi(id: number): Promise<void> {
  return request.delete(`/admin/uploads/${id}`)
}
