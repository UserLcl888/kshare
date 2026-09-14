import request from './request'
import type {
  ArticleDetailResp,
  ArticleListItem,
  ArticleQuery,
  LearnCategory,
  MarkdownPreview,
  PageResult
} from '@/types'

export interface ArticleSavePayload {
  title: string
  slug?: string
  summary?: string
  docUrl?: string
  columnType?: string
  learnCategoryId?: number
  categoryId?: number
  difficulty: string
  isPinned?: number
  coverUrl?: string
  /** 列表页封面缩略图（宽 800 的 WebP），由封面上传接口一并返回 */
  coverThumbUrl?: string
  tags: string[]
  contentMd?: string
}

export interface ArticleReorderItem {
  id: number
  sortOrder: number
}

export interface TopicReorderItem {
  id: number
  sortOrder: number
  isPinned: number
}

export async function getArticles(params: ArticleQuery = {}): Promise<PageResult<ArticleListItem>> {
  return request.get('/articles', { params })
}

export async function getTopicArticlesApi(params: {
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResult<ArticleListItem>> {
  return request.get('/topics/articles', { params })
}

export async function getLearnCategoriesApi(): Promise<LearnCategory[]> {
  return request.get('/learn/categories')
}

export async function getLearnArticlesApi(params: {
  categorySlug: string
  page?: number
  size?: number
}): Promise<PageResult<ArticleListItem>> {
  return request.get('/learn/articles', { params })
}

export async function getArticleDetail(slug: string): Promise<ArticleDetailResp> {
  return request.get(`/articles/${slug}`)
}

/**
 * 正文预览：交给服务端用与正文完全相同的规则渲染（flexmark + 白名单），
 * 保证「预览」和「发布后正文」一字不差。只渲染不落库。
 */
export async function previewArticleMdApi(contentMd: string): Promise<MarkdownPreview> {
  // 失败时前端会退回本地渲染，这里不弹全局错误提示
  return request.post('/admin/articles/preview', { contentMd }, { skipErrorToast: true, timeout: 30000 })
}

export async function recordViewApi(id: number): Promise<number> {
  return request.post(`/articles/${id}/view`)
}

export async function createArticleApi(
  payload: ArticleSavePayload
): Promise<{ id: number; slug: string; title: string; columnType?: string }> {
  // 保存文章时会下载/压缩正文图片，耗时较长，单独放宽超时
  return request.post('/admin/articles', payload, { timeout: 120000 })
}

export async function updateArticleApi(
  id: number,
  payload: ArticleSavePayload
): Promise<{ id: number; slug: string; title: string; columnType?: string }> {
  return request.put(`/admin/articles/${id}`, payload, { timeout: 120000 })
}

export async function reorderArticlesApi(items: ArticleReorderItem[]): Promise<void> {
  return request.put('/admin/articles/reorder', items)
}

export async function reorderTopicArticlesApi(items: TopicReorderItem[]): Promise<void> {
  return request.put('/admin/articles/topics/reorder', items)
}
