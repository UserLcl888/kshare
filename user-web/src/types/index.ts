export interface CategoryNode {
  id: number
  name: string
  slug: string
  parentId: number
  sortOrder: number
  description?: string
  accessLevel?: string
  children: CategoryNode[]
}

export interface ArticleListItem {
  id: number
  slug: string
  title: string
  summary: string
  columnType?: string
  categoryId: number
  difficulty: string
  isPinned?: number
  coverUrl?: string
  /** 列表页封面缩略图（宽 800 的 WebP），为空时回退 coverUrl */
  coverThumbUrl?: string
  tags: string[]
  viewCount: number
  updatedAt: string
}

export interface TocItem {
  id: string
  text: string
  level: number
}

/** Markdown 预览结果：服务端按正文同一套规则渲染的 HTML + 目录 */
export interface MarkdownPreview {
  contentHtml: string
  toc: TocItem[]
}

export interface ArticleDetail {
  id: number
  slug: string
  title: string
  summary: string
  docUrl: string
  columnType?: string
  learnCategoryId?: number | null
  categoryId: number
  categoryName: string
  categorySlug: string
  difficulty: string
  isPinned?: number
  coverUrl?: string
  /** 列表页封面缩略图（宽 800 的 WebP），为空时回退 coverUrl */
  coverThumbUrl?: string
  tags: string[]
  contentMd: string
  contentHtml: string
  toc: TocItem[]
  viewCount: number
  updatedAt: string
}

export interface ArticleBrief {
  id: number
  slug: string
  title: string
}

export interface LearnCategory {
  id: number
  slug: string
  name: string
  coverUrl: string
  /** 列表页封面缩略图（宽 800 的 WebP），为空时回退 coverUrl */
  coverThumbUrl?: string
  articleCount: number
  updatedAt: string | null
}

export interface NoticeItem {
  id: number
  content: string
  sortOrder: number
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface ArticleDetailResp {
  article: ArticleDetail
  prev: ArticleBrief | null
  next: ArticleBrief | null
}

export interface PageResult<T> {
  list: T[]
  page: number
  size: number
  total: number
  hasMore: boolean
}

export interface UserInfo {
  id: number
  nickname: string
  avatar?: string
  email: string
  phone?: string
  role: string
  status?: number
  createdAt: string
}

export interface LoginResult {
  token: string
  userInfo: UserInfo
}

export interface ArticleQuery {
  categoryId?: number
  difficulty?: string
  page?: number
  size?: number
}

export interface UserUploadItem {
  id: number
  userId: number
  title: string
  categoryName: string
  groupName: string
  fileName: string
  status: number
  /** 处理状态：0=处理中 1=已完成 2=处理失败（后端异步处理图片与正文） */
  processStatus?: number
  adminReply: string
  repliedAt: string | null
  createdAt: string
  nickname?: string
  email?: string
  phone?: string
}

export interface UserUploadDetail extends UserUploadItem {
  contentMd: string
  contentHtml: string
}

export interface NotificationItem {
  id: number
  type: string
  content: string
  uploadId: number | null
  isRead: number
  createdAt: string
}

export interface AccessApplyItem {
  id: number
  userId: number
  scope: string
  categoryId: number | null
  categoryName: string
  categorySlug: string | null
  reason: string
  status: number
  adminReply: string
  reviewRemark: string
  createdAt: string
  reviewedAt: string | null
  nickname?: string
  email?: string
  phone?: string
}

export interface LockedCategoryItem {
  id: number
  name: string
  slug: string
}

export interface AccessStatusItem {
  type: 'ARTICLE' | 'CATEGORY'
  id: number
  title: string
  status: 'NONE' | 'PENDING' | 'REJECTED' | 'GRANTED'
  reviewRemark: string | null
  categoryId: number | null
  categoryName: string | null
}
