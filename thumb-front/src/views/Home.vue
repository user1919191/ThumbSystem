<template>
  <div class="home-page">
    <!-- 英雄区域 -->
    <div class="hero-section">
      <div class="container-custom">
        <div class="hero-content">
          <h1 class="hero-title">
            <span class="gradient-text">发现精彩</span>
            <br />
            <span class="text-gray-800">博客世界</span>
          </h1>
          <p class="hero-subtitle">
            探索优质内容，分享知识见解，与志同道合的人一起成长
          </p>
          
          <!-- 搜索框 -->
          <div class="search-container">
            <div class="search-box">
              <svg class="search-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <input 
                v-model="searchQuery"
                type="text" 
                placeholder="搜索博客内容..." 
                class="search-input"
                @input="handleSearch"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="container-custom py-8">
      <div class="page-header">
        <h2 class="section-title">
          <span class="gradient-text">精选博客</span>
          <span class="blog-count">({{ filteredBlogs.length }})</span>
        </h2>
        <div class="header-actions">
          <div class="view-controls">
            <button 
              @click="toggleViewMode" 
              class="view-button"
              :class="{ active: viewMode === 'grid' }"
            >
              <svg class="view-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
              </svg>
              <span>网格视图</span>
            </button>
            <button 
              @click="toggleViewMode" 
              class="view-button"
              :class="{ active: viewMode === 'ranking' }"
            >
              <svg class="view-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
              </svg>
              <span>点赞排行</span>
            </button>
          </div>
          <LoginBadge />
        </div>
      </div>
      
      <ApiLoader :loading="blogStore.loading" :fullscreen="true" />
      
      <template v-if="!blogStore.loading && filteredBlogs.length === 0">
        <div class="empty-state">
          <div class="empty-icon">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </div>
          <p class="empty-text">
            {{ searchQuery ? '没有找到相关博客' : '暂无博客内容' }}
          </p>
          <button v-if="searchQuery" @click="clearSearch" class="btn btn-outline">
            清除搜索
          </button>
        </div>
      </template>
      
      <!-- 网格视图 -->
      <div v-if="viewMode === 'grid'" class="blog-grid">
        <div 
          v-for="(blog, index) in filteredBlogs" 
          :key="blog.id" 
          class="blog-card-container card"
          :style="{ animationDelay: `${index * 0.1}s` }"
        >
          <router-link 
            :to="`/blog/${blog.id}`"
            class="blog-content-link"
          >
            <div class="blog-cover">
              <img :src="blog.coverImg" :alt="blog.title" class="cover-img" referrerpolicy="no-referrer" />
              <div class="cover-overlay">
                <div class="read-more">
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                  </svg>
                  <span>阅读更多</span>
                </div>
              </div>
            </div>
            <div class="blog-content">
              <h3 class="blog-title">{{ blog.title }}</h3>
              <p class="blog-summary">{{ truncateString(blog.content, 100) }}</p>
            </div>
          </router-link>
          
          <div class="blog-meta">
            <div class="blog-time">{{ formatDate(blog.createTime) }}</div>
            <ThumbButton 
              :blog-id="blog.id" 
              :count="blog.thumbCount" 
              :has-thumb="blog.hasThumb" 
            />
          </div>
        </div>
      </div>

      <!-- 排行榜视图 -->
      <div v-else class="ranking-container">
        <div class="ranking-header">
          <h3 class="ranking-title">
            <svg class="ranking-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
            点赞排行榜
          </h3>
          <p class="ranking-subtitle">根据点赞数量排序的热门博客</p>
        </div>
        
        <div class="ranking-list">
          <div 
            v-for="(blog, index) in sortedBlogsByThumbs" 
            :key="blog.id" 
            class="ranking-item"
            :class="getRankingClass(index)"
            :style="{ animationDelay: `${index * 0.1}s` }"
          >
            <div class="rank-number">
              <span class="rank-badge" :class="getRankBadgeClass(index)">
                {{ index + 1 }}
              </span>
            </div>
            
            <router-link 
              :to="`/blog/${blog.id}`"
              class="ranking-content"
            >
              <div class="ranking-cover">
                <img :src="blog.coverImg" :alt="blog.title" class="ranking-img" referrerpolicy="no-referrer" />
              </div>
              <div class="ranking-info">
                <h4 class="ranking-blog-title">{{ blog.title }}</h4>
                <p class="ranking-summary">{{ truncateString(blog.content, 80) }}</p>
                <div class="ranking-meta">
                  <span class="ranking-time">{{ formatDate(blog.createTime) }}</span>
                </div>
              </div>
            </router-link>
            
            <div class="ranking-stats">
              <div class="thumb-count-display">
                <svg class="thumb-icon" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M7.493 18.75c-.425 0-.82-.236-.975-.632A7.48 7.48 0 016 15.375c0-1.75.599-3.358 1.602-4.634.151-.192.373-.309.6-.397.473-.183.89-.514 1.212-.924a9.042 9.042 0 012.861-2.4c.723-.384 1.35-.956 1.653-1.715a4.498 4.498 0 00.322-1.672V3a.75.75 0 01.75-.75 2.25 2.25 0 012.25 2.25c0 1.152-.26 2.243-.723 3.218-.266.558.107 1.282.725 1.282h3.126c1.026 0 1.945.694 2.054 1.715.045.422.068.85.068 1.285a11.95 11.95 0 01-2.649 7.521c-.388.482-.987.729-1.605.729H14.23c-.483 0-.964-.078-1.423-.23l-3.114-1.04a4.501 4.501 0 00-1.423-.23h-.777zM2.331 10.977a11.969 11.969 0 00-.831 4.398 12 12 0 00.52 3.507c.26.85 1.084 1.368 1.973 1.368H4.9c.445 0 .72-.498.523-.898a8.963 8.963 0 01-.924-3.977c0-1.708.476-3.305 1.302-4.666.245-.403-.028-.959-.5-.959H4.25c-.832 0-1.612.453-1.918 1.227z" />
                </svg>
                <span class="count-number">{{ blog.thumbCount }}</span>
              </div>
              <ThumbButton 
                :blog-id="blog.id" 
                :count="blog.thumbCount" 
                :has-thumb="blog.hasThumb" 
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useVirtualList } from '@vueuse/core';
import ThumbButton from '../components/ThumbButton.vue';
import LoginBadge from '../components/common/LoginBadge.vue';
import ApiLoader from '../components/common/ApiLoader.vue';
import { useBlogStore } from '../stores/blog';
import { useUserStore } from '../stores/user';
import { formatDate, truncateString } from '../utils';

const blogStore = useBlogStore();
const userStore = useUserStore();

// 搜索功能
const searchQuery = ref('');

// 视图模式
const viewMode = ref('grid'); // 'grid' 或 'ranking'

// 获取博客列表
const fetchBlogs = async () => {
  await blogStore.fetchBlogs();
};

// 使用计算属性获取博客列表，确保响应式
const blogs = computed(() => blogStore.blogs);

// 过滤博客列表
const filteredBlogs = computed(() => {
  if (!searchQuery.value.trim()) {
    return blogs.value;
  }
  
  const query = searchQuery.value.toLowerCase().trim();
  return blogs.value.filter(blog => 
    blog.title.toLowerCase().includes(query) ||
    blog.content.toLowerCase().includes(query)
  );
});

// 按点赞数排序的博客列表
const sortedBlogsByThumbs = computed(() => {
  return [...filteredBlogs.value].sort((a, b) => b.thumbCount - a.thumbCount);
});

// 切换视图模式
const toggleViewMode = () => {
  viewMode.value = viewMode.value === 'grid' ? 'ranking' : 'grid';
};

// 获取排行榜项目的样式类
const getRankingClass = (index) => {
  if (index === 0) return 'rank-first';
  if (index === 1) return 'rank-second';
  if (index === 2) return 'rank-third';
  return 'rank-normal';
};

// 获取排名徽章的样式类
const getRankBadgeClass = (index) => {
  if (index === 0) return 'badge-gold';
  if (index === 1) return 'badge-silver';
  if (index === 2) return 'badge-bronze';
  return 'badge-normal';
};

// 处理搜索
const handleSearch = () => {
  // 搜索逻辑已在computed中处理
};

// 清除搜索
const clearSearch = () => {
  searchQuery.value = '';
};

// 初始化
onMounted(async () => {
  // 尝试恢复登录状态
  await userStore.restoreLogin();
  // 获取博客列表
  await fetchBlogs();
});
</script>

<style scoped>
.home-page {
  @apply min-h-screen;
}

/* 英雄区域样式 */
.hero-section {
  @apply relative py-20 overflow-hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.hero-section::before {
  content: '';
  @apply absolute inset-0;
  background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><pattern id="grain" width="100" height="100" patternUnits="userSpaceOnUse"><circle cx="25" cy="25" r="1" fill="white" opacity="0.1"/><circle cx="75" cy="75" r="1" fill="white" opacity="0.1"/><circle cx="50" cy="10" r="0.5" fill="white" opacity="0.1"/><circle cx="10" cy="60" r="0.5" fill="white" opacity="0.1"/><circle cx="90" cy="40" r="0.5" fill="white" opacity="0.1"/></pattern></defs><rect width="100" height="100" fill="url(%23grain)"/></svg>');
}

.hero-content {
  @apply relative z-10 text-center max-w-4xl mx-auto;
}

.hero-title {
  @apply text-5xl md:text-6xl font-bold mb-6 leading-tight;
}

.hero-subtitle {
  @apply text-xl text-white text-opacity-90 mb-8 max-w-2xl mx-auto;
}

/* 搜索框样式 */
.search-container {
  @apply max-w-2xl mx-auto;
}

.search-box {
  @apply relative flex items-center;
}

.search-icon {
  @apply absolute left-4 w-5 h-5 text-gray-400 z-10;
}

.search-input {
  @apply w-full pl-12 pr-4 py-4 text-lg rounded-full border-0 shadow-lg;
  @apply bg-white bg-opacity-95 backdrop-blur-sm;
  @apply focus:outline-none focus:ring-4 focus:ring-white focus:ring-opacity-30;
  @apply placeholder-gray-400;
}

/* 页面头部 */
.page-header {
  @apply flex justify-between items-center mb-8;
}

.section-title {
  @apply text-3xl font-bold flex items-center gap-3;
}

.blog-count {
  @apply text-lg font-normal text-gray-500;
}

.header-actions {
  @apply flex items-center gap-4;
}

.view-controls {
  @apply flex items-center gap-2;
}

.view-button {
  @apply flex items-center gap-2 px-4 py-2 rounded-lg;
  @apply text-gray-600 hover:text-blue-600 hover:bg-blue-50;
  @apply transition-all duration-200;
}

.view-button.active {
  @apply bg-blue-100 text-blue-600;
}

.view-icon {
  @apply w-5 h-5;
}

/* 空状态 */
.empty-state {
  @apply flex flex-col items-center justify-center py-20 text-center;
}

.empty-icon {
  @apply w-16 h-16 text-gray-300 mb-4;
}

.empty-text {
  @apply text-gray-500 text-lg mb-4;
}

/* 博客网格 */
.blog-grid {
  @apply grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8;
}

.blog-card-container {
  @apply flex flex-col h-full;
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.blog-content-link {
  @apply block no-underline text-inherit transition-all duration-300;
}

.blog-cover {
  @apply w-full overflow-hidden relative;
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%);
}

.cover-img {
  @apply w-full h-full object-cover transition-transform duration-500;
}

.cover-overlay {
  @apply absolute inset-0 bg-black bg-opacity-0 transition-all duration-300;
  @apply flex items-center justify-center;
}

.blog-card-container:hover .cover-overlay {
  @apply bg-black bg-opacity-40;
}

.read-more {
  @apply flex items-center gap-2 text-white opacity-0 transform translate-y-4;
  @apply transition-all duration-300;
}

.blog-card-container:hover .read-more {
  @apply opacity-100 translate-y-0;
}

.blog-content {
  @apply flex flex-col flex-grow p-6;
}

.blog-title {
  @apply text-xl font-bold text-gray-800 mb-3 line-clamp-2;
  @apply hover:text-blue-600 transition-colors duration-200;
}

.blog-summary {
  @apply text-gray-600 mb-4 line-clamp-3 leading-relaxed;
}

.blog-meta {
  @apply flex justify-between items-center p-6 pt-0 mt-auto;
  @apply border-t border-gray-100;
}

.blog-time {
  @apply text-sm text-gray-500;
}

/* 排行榜样式 */
.ranking-container {
  @apply max-w-6xl mx-auto;
}

.ranking-header {
  @apply text-center mb-8;
}

.ranking-title {
  @apply text-2xl font-bold text-gray-800 mb-2 flex items-center justify-center gap-3;
}

.ranking-icon {
  @apply w-6 h-6 text-blue-600;
}

.ranking-subtitle {
  @apply text-gray-600;
}

.ranking-list {
  @apply space-y-4;
}

.ranking-item {
  @apply bg-white rounded-xl shadow-lg p-6 flex items-center gap-6;
  @apply hover:shadow-xl transition-all duration-300 transform hover:-translate-y-1;
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
}

.rank-first {
  @apply border-l-4 border-yellow-400 bg-gradient-to-r from-yellow-50 to-white;
}

.rank-second {
  @apply border-l-4 border-gray-300 bg-gradient-to-r from-gray-50 to-white;
}

.rank-third {
  @apply border-l-4 border-orange-400 bg-gradient-to-r from-orange-50 to-white;
}

.rank-normal {
  @apply border-l-4 border-gray-200;
}

.rank-number {
  @apply flex-shrink-0;
}

.rank-badge {
  @apply w-12 h-12 rounded-full flex items-center justify-center;
  @apply text-white font-bold text-lg shadow-lg;
}

.badge-gold {
  @apply bg-gradient-to-br from-yellow-400 to-yellow-600;
}

.badge-silver {
  @apply bg-gradient-to-br from-gray-400 to-gray-600;
}

.badge-bronze {
  @apply bg-gradient-to-br from-orange-400 to-orange-600;
}

.badge-normal {
  @apply bg-gradient-to-br from-blue-400 to-blue-600;
}

.ranking-content {
  @apply flex items-center gap-4 flex-grow no-underline text-inherit;
}

.ranking-cover {
  @apply w-20 h-20 rounded-lg overflow-hidden flex-shrink-0;
}

.ranking-img {
  @apply w-full h-full object-cover;
}

.ranking-info {
  @apply flex-grow;
}

.ranking-blog-title {
  @apply text-lg font-semibold text-gray-800 mb-1 line-clamp-2;
  @apply hover:text-blue-600 transition-colors duration-200;
}

.ranking-summary {
  @apply text-sm text-gray-600 mb-2 line-clamp-2;
}

.ranking-meta {
  @apply flex items-center gap-4;
}

.ranking-time {
  @apply text-xs text-gray-500;
}

.ranking-stats {
  @apply flex flex-col items-end gap-2 flex-shrink-0;
}

.thumb-count-display {
  @apply flex items-center gap-1 text-gray-600;
}

.thumb-icon {
  @apply w-4 h-4;
}

.count-number {
  @apply text-lg font-bold;
}

/* 响应式设计 */
@media (max-width: 640px) {
  .hero-title {
    @apply text-4xl;
  }
  
  .hero-subtitle {
    @apply text-lg;
  }
  
  .search-input {
    @apply py-3 text-base;
  }
  
  .blog-grid {
    @apply grid-cols-1 gap-6;
  }
  
  .page-header {
    @apply flex-col gap-4 items-start;
  }
  
  .header-actions {
    @apply w-full justify-between;
  }
  
  .ranking-item {
    @apply flex-col gap-4 p-4;
  }
  
  .ranking-content {
    @apply w-full;
  }
  
  .ranking-cover {
    @apply w-16 h-16;
  }
  
  .ranking-stats {
    @apply flex-row justify-between w-full;
  }
}
</style> 