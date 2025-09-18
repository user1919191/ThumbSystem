<template>
  <div class="markdown-content" v-html="renderedMarkdown"></div>
</template>

<script setup>
import { computed } from 'vue';
import { marked } from 'marked';

const props = defineProps({
  content: {
    type: String,
    required: true
  }
});

// 配置marked选项
marked.setOptions({
  breaks: true, // 支持换行符转换为<br>
  gfm: true,    // 启用GitHub风格的Markdown
});

// 渲染Markdown内容
const renderedMarkdown = computed(() => {
  if (!props.content) return '';
  return marked(props.content);
});
</script>

<style scoped>
.markdown-content {
  @apply text-gray-700 leading-relaxed;
}

/* Markdown样式 */
.markdown-content :deep(h1) {
  @apply text-2xl font-bold text-gray-800 mt-6 mb-4 pb-2 border-b border-gray-200;
}

.markdown-content :deep(h2) {
  @apply text-xl font-bold text-gray-800 mt-5 mb-3 pb-1 border-b border-gray-100;
}

.markdown-content :deep(h3) {
  @apply text-lg font-semibold text-gray-800 mt-4 mb-2;
}

.markdown-content :deep(h4) {
  @apply text-base font-semibold text-gray-800 mt-3 mb-2;
}

.markdown-content :deep(h5) {
  @apply text-sm font-semibold text-gray-800 mt-3 mb-2;
}

.markdown-content :deep(h6) {
  @apply text-sm font-medium text-gray-600 mt-3 mb-2;
}

.markdown-content :deep(p) {
  @apply mb-4;
}

.markdown-content :deep(strong) {
  @apply font-semibold text-gray-800;
}

.markdown-content :deep(em) {
  @apply italic;
}

.markdown-content :deep(code) {
  @apply bg-gray-100 text-red-600 px-1 py-0.5 rounded text-sm font-mono;
}

.markdown-content :deep(pre) {
  @apply bg-gray-100 p-4 rounded-lg overflow-x-auto mb-4;
}

.markdown-content :deep(pre code) {
  @apply bg-transparent text-gray-800 p-0;
}

.markdown-content :deep(blockquote) {
  @apply border-l-4 border-gray-300 pl-4 italic text-gray-600 my-4;
}

.markdown-content :deep(ul) {
  @apply list-disc list-inside mb-4 space-y-1;
}

.markdown-content :deep(ol) {
  @apply list-decimal list-inside mb-4 space-y-1;
}

.markdown-content :deep(li) {
  @apply mb-1;
}

.markdown-content :deep(a) {
  @apply text-blue-600 hover:text-blue-800 underline;
}

.markdown-content :deep(img) {
  @apply max-w-full h-auto rounded-lg shadow-sm my-4;
}

.markdown-content :deep(table) {
  @apply w-full border-collapse border border-gray-300 mb-4;
}

.markdown-content :deep(th) {
  @apply border border-gray-300 bg-gray-50 px-4 py-2 text-left font-semibold;
}

.markdown-content :deep(td) {
  @apply border border-gray-300 px-4 py-2;
}

.markdown-content :deep(hr) {
  @apply border-0 border-t border-gray-300 my-6;
}
</style>
