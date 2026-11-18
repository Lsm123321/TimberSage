<template>
  <div class="profile-container">
    <div class="profile-header">
      <!-- 整块可点：点头像或下方按钮都能唤起文件选择；也支持直接把图片拖进来 -->
      <div
        class="avatar-wrap"
        :class="{ uploading: avatarUploading, 'is-dragover': avatarDragOver }"
        @click="triggerAvatarPick"
        @dragover.prevent="avatarDragOver = true"
        @dragenter.prevent="avatarDragOver = true"
        @dragleave.prevent="avatarDragOver = false"
        @drop.prevent="handleAvatarDrop"
        :title="t('changeAvatar')"
      >
        <el-avatar :size="80" :src="userInfo.avatar">
          {{ userInfo.nickname?.charAt(0) || userInfo.username?.charAt(0) }}
        </el-avatar>
        <div class="avatar-mask">
          <el-icon v-if="!avatarUploading"><Camera /></el-icon>
          <el-icon v-else class="is-loading-icon"><Loading /></el-icon>
          <span>{{ avatarUploading ? t('uploading') : t('changeAvatar') }}</span>
        </div>
        <input
          ref="avatarInputRef"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif,image/bmp"
          class="avatar-input"
          @change="handleAvatarChange"
        />
      </div>
      <div class="user-info">
        <h2>{{ userInfo.nickname || userInfo.username }}</h2>
        <p>{{ userInfo.email || t('noEmail') }}</p>
        <div class="user-tags">
          <el-tag :type="getRoleType(userInfo.role)">{{ getRoleText(userInfo.role) }}</el-tag>
          <el-button type="primary" plain size="small" @click="showContributionDialog" v-if="userInfo.role === 'user'">
            {{ t('applyContributor') }}
          </el-button>
          <el-tag type="success" v-if="contributionStatus === 'pending'">{{ t('contributorPending') }}</el-tag>
          <el-button
            v-if="userInfo.role === 'contributor' || userInfo.role === 'admin'"
            type="primary"
            plain
            size="small"
            @click="openModelUploadDialog"
          >
            {{ t('uploadModel') }}
          </el-button>
        </div>
      </div>

      <!-- 概览统计：让用户一眼看到自己有多少内容，不必逐个标签去翻 -->
      <div class="profile-stats">
        <div class="stat-item" @click="switchTab('favorites')">
          <span class="stat-value">{{ favoritesTotal }}</span>
          <span class="stat-label">{{ t('favorites') }}</span>
        </div>
        <div class="stat-item" @click="switchTab('likes')">
          <span class="stat-value">{{ likesTotal }}</span>
          <span class="stat-label">{{ t('myLikes') }}</span>
        </div>
        <div class="stat-item" @click="switchTab('comments')">
          <span class="stat-value">{{ commentsTotal }}</span>
          <span class="stat-label">{{ t('myComments') }}</span>
        </div>
        <div class="stat-item" @click="switchTab('downloads')">
          <span class="stat-value">{{ downloadsTotal }}</span>
          <span class="stat-label">{{ t('myDownloads') }}</span>
        </div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="profile-tabs" :tab-position="tabPosition">
      <el-tab-pane :label="t('profileBasicInfo')" name="info">
        <el-card>
          <template #header>
            <span>{{ t('profileBasicInfo') }}</span>
          </template>
          <el-form :model="form" label-width="100px" class="profile-form">
            <el-form-item :label="t('username')">
              <el-input v-model="form.username" disabled />
            </el-form-item>
            <el-form-item :label="t('nickname')">
              <el-input v-model="form.nickname" :placeholder="t('enterNickname')" />
            </el-form-item>
            <el-form-item :label="t('email')">
              <el-input v-model="form.email" :placeholder="t('enterEmail')" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSave" :loading="saving">{{ t('save') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="t('changePassword')" name="password">
        <el-card>
          <template #header>
            <span>{{ t('changePassword') }}</span>
          </template>
          <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px" class="profile-form">
            <el-form-item :label="t('oldPassword')" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="t('newPassword')" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="t('confirmPassword')" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleChangePassword" :loading="changing">{{ t('update') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="t('favorites')" name="favorites">
        <el-card>
          <template #header>
            <div class="tab-header-row">
              <span>{{ t('favorites') }}</span>
              <el-input
                v-model="favoritesKeyword"
                :placeholder="t('searchFavoritesModel')"
                clearable
                class="tab-search-input"
              />
            </div>
          </template>
          <div v-if="favorites.length === 0" class="empty-state">
            <el-empty :description="t('noFavorites')" />
          </div>
          <div v-else-if="filteredFavorites.length === 0" class="empty-state">
            <el-empty :description="t('noFavoriteSearchResult')" />
          </div>
          <div v-else class="model-card-grid">
            <div v-for="item in filteredFavorites" :key="item.modelId" class="model-card">
              <div class="card-image" @click="goToDetail(item.modelId)">
                <img :src="item.thumbnailPath || defaultImage" :alt="item.modelName" />
                <div class="card-overlay">
                  <span class="view-btn">{{ t('viewDetail') }}</span>
                </div>
                <div class="card-badges">
                  <el-tag v-if="item.categoryName" size="small" type="info">{{ item.categoryName }}</el-tag>
                </div>
              </div>
              <div class="card-content">
                <h3 class="model-name">{{ item.modelName }}</h3>
                <p class="dynasty" v-if="item.dynasty">{{ item.dynasty }}</p>
                <p class="location" v-if="item.location">{{ item.location }}</p>
                <div class="card-stats">
                  <span>{{ item.viewCount || 0 }} {{ t('views') }}</span>
                  <span>{{ item.likeCount || 0 }} {{ t('likes') }}</span>
                  <span>{{ item.downloadCount || 0 }} {{ t('downloads') }}</span>
                </div>
                <div class="card-actions">
                  <el-button type="danger" size="small" @click.stop="removeFavorite(item.modelId)">{{ t('cancelFavorite') }}</el-button>
                </div>
              </div>
            </div>
            <el-pagination
              v-if="favoritesTotal > pageSize"
              layout="prev, pager, next"
              :total="favoritesTotal"
              :page-size="pageSize"
              v-model:current-page="favoritesPage"
              @current-change="loadFavorites"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="t('myLikes')" name="likes">
        <el-card>
          <template #header>
            <div class="tab-header-row">
              <span>{{ t('myLikes') }}</span>
              <el-input
                v-model="likesKeyword"
                :placeholder="t('searchLikedModel')"
                clearable
                class="tab-search-input"
              />
            </div>
          </template>
          <div v-if="likes.length === 0" class="empty-state">
            <el-empty :description="t('noLikes')" />
          </div>
          <div v-else-if="filteredLikes.length === 0" class="empty-state">
            <el-empty :description="t('noLikeSearchResult')" />
          </div>
          <div v-else class="model-card-grid">
            <div v-for="item in filteredLikes" :key="item.modelId" class="model-card">
              <div class="card-image" @click="goToDetail(item.modelId)">
                <img :src="item.thumbnailPath || defaultImage" :alt="item.modelName" />
                <div class="card-overlay">
                  <span class="view-btn">{{ t('viewDetail') }}</span>
                </div>
                <div class="card-badges">
                  <el-tag v-if="item.categoryName" size="small" type="info">{{ item.categoryName }}</el-tag>
                </div>
              </div>
              <div class="card-content">
                <h3 class="model-name">{{ item.modelName }}</h3>
                <p class="dynasty" v-if="item.dynasty">{{ item.dynasty }}</p>
                <p class="location" v-if="item.location">{{ item.location }}</p>
                <div class="card-stats">
                  <span>{{ item.viewCount || 0 }} {{ t('views') }}</span>
                  <span>{{ item.likeCount || 0 }} {{ t('likes') }}</span>
                  <span>{{ item.downloadCount || 0 }} {{ t('downloads') }}</span>
                </div>
                <div class="card-actions">
                  <el-button type="info" size="small" @click.stop="removeLike(item.modelId)">{{ t('cancelLike') }}</el-button>
                </div>
              </div>
            </div>
            <el-pagination
              v-if="likesTotal > pageSize"
              layout="prev, pager, next"
              :total="likesTotal"
              :page-size="pageSize"
              v-model:current-page="likesPage"
              @current-change="loadLikes"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="t('myComments')" name="comments">
        <el-card>
          <template #header>
            <span>{{ t('myComments') }}</span>
          </template>
          <div v-if="comments.length === 0" class="empty-state">
            <el-empty :description="t('noComments')" />
          </div>
          <div v-else class="list-content">
            <div v-for="item in comments" :key="item.id" class="list-item">
              <div class="item-info">
                <div class="item-title" @click="goToDetail(item.modelId)">{{ item.modelName }}</div>
                <div class="item-content">{{ item.content }}</div>
                <div class="item-meta">{{ item.createdAt }}</div>
              </div>
              <el-button type="danger" size="small" @click="deleteComment(item.id)">{{ t('delete') }}</el-button>
            </div>
            <el-pagination
              v-if="commentsTotal > pageSize"
              layout="prev, pager, next"
              :total="commentsTotal"
              :page-size="pageSize"
              v-model:current-page="commentsPage"
              @current-change="loadComments"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="t('qaHistory')" name="chatHistory">
        <el-card>
          <template #header>
            <div class="chat-history-header">
              <span>{{ t('qaHistory') }}</span>
              <el-tag type="success">{{ t('chatHistorySupport') }}</el-tag>
            </div>
          </template>
          <div v-if="chatHistory.length === 0" class="empty-state">
            <el-empty :description="t('noQaHistory')" />
          </div>
          <div v-else class="list-content">
            <div v-for="item in chatHistory" :key="item.id" class="list-item">
              <div class="item-info">
                <div class="item-title" v-if="item.modelName">{{ t('relatedModelLabel') }}：{{ item.modelName }}</div>
                <div class="item-source">
                  <el-tag size="small" type="success">{{ item.sourceLabel || '模型聊天' }}</el-tag>
                </div>
                <div class="item-question">{{ t('questionLabel') }}：{{ item.userQuestion }}</div>
                <div class="item-answer">{{ t('answerLabel') }}：{{ item.aiAnswer }}</div>
                <div class="item-meta">
                  {{ item.createdAt }} · {{ item.tokensUsed || 0 }} tokens
                </div>
                <div class="chat-note" v-if="item.chatNote">
                  <div class="note-label">{{ t('noteLabel') }}：</div>
                  <div class="note-content">{{ item.chatNote }}</div>
                </div>
              </div>
              <div class="item-actions">
                <el-button size="small" @click="openChatNoteEditor(item)">{{ t('editNotes') }}</el-button>
                <el-button size="small" type="primary" @click="generateChatNoteAction(item)">{{ t('generateNotes') }}</el-button>
                <el-button size="small" type="danger" @click="deleteChatHistoryAction(item.id)">{{ t('delete') }}</el-button>
              </div>
            </div>
            <el-pagination
              v-if="chatHistoryTotal > pageSize"
              layout="prev, pager, next"
              :total="chatHistoryTotal"
              :page-size="pageSize"
              v-model:current-page="chatHistoryPage"
              @current-change="loadChatHistory"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="t('myDownloads')" name="downloads">
        <el-card>
          <template #header>
            <span>{{ t('myDownloads') }}</span>
          </template>
          <div v-if="downloads.length === 0" class="empty-state">
            <el-empty :description="t('noDownloads')" />
          </div>
          <div v-else class="list-content">
            <div v-for="item in downloads" :key="item.id" class="list-item" @click="goToDetail(item.modelId)">
              <div class="item-info">
                <div class="item-title">{{ item.modelName }}</div>
                <div class="item-meta">{{ item.downloadTime }}</div>
              </div>
            </div>
            <el-pagination
              v-if="downloadsTotal > pageSize"
              layout="prev, pager, next"
              :total="downloadsTotal"
              :page-size="pageSize"
              v-model:current-page="downloadsPage"
              @current-change="loadDownloads"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane
        v-if="userInfo.role === 'contributor' || userInfo.role === 'admin'"
        :label="t('myPendingModels')"
        name="myPendingModels"
      >
        <el-card>
          <template #header>
            <span>{{ t('myPendingModels') }}</span>
          </template>
          <div v-if="pendingModels.length === 0" class="empty-state">
            <el-empty :description="t('noPendingModels')" />
          </div>
          <div v-else class="model-card-grid">
            <div v-for="item in pendingModels" :key="item.id" class="model-card">
              <!-- 待审核模型只展示状态，不再提供跳转后台的入口 -->
              <div class="card-image">
                <img :src="item.thumbnailPath || defaultImage" :alt="item.modelName" />
                <div class="card-badges">
                  <el-tag size="small" type="warning">{{ t('pendingReview') }}</el-tag>
                  <el-tag v-if="item.categoryName" size="small" type="info">{{ item.categoryName }}</el-tag>
                </div>
              </div>
              <div class="card-content">
                <h3 class="model-name">{{ item.modelName }}</h3>
                <p class="dynasty" v-if="item.dynasty">{{ item.dynasty }}</p>
                <p class="location" v-if="item.location">{{ item.location }}</p>
                <div class="card-stats">
                  <span>{{ item.viewCount || 0 }} {{ t('views') }}</span>
                  <span>{{ item.likeCount || 0 }} {{ t('likes') }}</span>
                  <span>{{ item.downloadCount || 0 }} {{ t('downloads') }}</span>
                </div>
              </div>
            </div>
            <el-pagination
              v-if="pendingModelsTotal > pageSize"
              layout="prev, pager, next"
              :total="pendingModelsTotal"
              :page-size="pageSize"
              v-model:current-page="pendingModelsPage"
              @current-change="loadPendingModels"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane
        v-if="userInfo.role === 'contributor' || userInfo.role === 'admin'"
        :label="t('myRejectedModels')"
        name="myRejectedModels"
      >
        <el-card>
          <template #header>
            <span>{{ t('myRejectedModels') }}</span>
          </template>
          <div v-if="rejectedModels.length === 0" class="empty-state">
            <el-empty :description="t('noRejectedModels')" />
          </div>
          <div v-else class="model-card-grid">
            <div v-for="item in rejectedModels" :key="item.id" class="model-card">
              <div class="card-image" @click="goToDetail(item.id)">
                <img :src="item.thumbnailPath || defaultImage" :alt="item.modelName" />
                <div class="card-overlay">
                  <span class="view-btn">{{ t('viewDetail') }}</span>
                </div>
                <div class="card-badges">
                  <el-tag size="small" type="danger">{{ t('rejected') }}</el-tag>
                  <el-tag v-if="item.categoryName" size="small" type="info">{{ item.categoryName }}</el-tag>
                </div>
              </div>
              <div class="card-content">
                <h3 class="model-name">{{ item.modelName }}</h3>
                <p class="dynasty" v-if="item.dynasty">{{ item.dynasty }}</p>
                <p class="location" v-if="item.location">{{ item.location }}</p>
                <p class="reject-reason" v-if="item.rejectReason">{{ t('rejectReason') }}：{{ item.rejectReason }}</p>
                <div class="card-stats">
                  <span>{{ item.viewCount || 0 }} {{ t('views') }}</span>
                  <span>{{ item.likeCount || 0 }} {{ t('likes') }}</span>
                  <span>{{ item.downloadCount || 0 }} {{ t('downloads') }}</span>
                </div>
              </div>
            </div>
            <el-pagination
              v-if="rejectedModelsTotal > pageSize"
              layout="prev, pager, next"
              :total="rejectedModelsTotal"
              :page-size="pageSize"
              v-model:current-page="rejectedModelsPage"
              @current-change="loadRejectedModels"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="t('materialPresets')" name="materialPresets">
        <el-card>
          <template #header>
            <span>{{ t('materialPresets') }}</span>
          </template>
          <div v-if="materialPresets.length === 0" class="empty-state">
            <el-empty :description="t('noMaterialPresets')" />
          </div>
          <div v-else class="list-content">
            <div v-for="item in materialPresets" :key="item.id" class="list-item">
              <div class="item-info">
                <div class="item-title">{{ item.presetName }}</div>
                <div class="item-meta">{{ t('modelIdLabel') }}: {{ item.modelId }} | {{ t('createdAtLabel') }}: {{ item.createdAt }}</div>
              </div>
              <div class="item-actions">
                <el-button type="primary" size="small" @click="applyMaterialPreset(item)">{{ t('apply') }}</el-button>
                <el-button type="danger" size="small" @click="deleteMaterialPresetAction(item.id)">{{ t('delete') }}</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="contributionDialogVisible"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="t('applyContributorTitle')"
      width="500px"
    >
      <el-form :model="contributionForm" label-width="100px">
        <el-form-item :label="t('applicationDescription')">
          <el-input
            v-model="contributionForm.description"
            type="textarea"
            :rows="6"
            :placeholder="t('applicationDescriptionPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contributionDialogVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" @click="submitContribution" :loading="submitting">{{ t('submitApplication') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="modelUploadDialogVisible"
      class="model-upload-dialog"
      modal-class="model-upload-overlay"
      :title="t('upload3DModel')"
      width="760px"
    >
      <el-form :model="modelUploadForm" label-width="120px">
        <el-form-item :label="t('modelName')" required>
          <el-input v-model="modelUploadForm.modelName" :placeholder="t('enterModelName')" />
        </el-form-item>
        <el-form-item :label="t('modelCategory')">
          <el-select v-model="modelUploadForm.categoryId" :placeholder="t('selectCategoryOptional')" clearable style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dynasty')">
          <el-input v-model="modelUploadForm.dynasty" :placeholder="t('exampleDynasty')" />
        </el-form-item>
        <el-form-item :label="t('location')">
          <el-input v-model="modelUploadForm.location" :placeholder="t('exampleLocation')" />
        </el-form-item>
        <el-form-item :label="t('modelDescription')">
          <el-input v-model="modelUploadForm.description" type="textarea" :rows="3" :placeholder="t('modelDescriptionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('tagKeywords')">
          <div class="tag-editor">
            <div class="tag-list" v-if="modelUploadForm.tags.length > 0">
              <el-tag
                v-for="(tag, index) in modelUploadForm.tags"
                :key="`${tag}-${index}`"
                closable
                @close="removeUploadTag(index)"
              >
                {{ tag }}
              </el-tag>
            </div>
            <el-input v-model="uploadTagInput" :placeholder="t('tagInputPlaceholder')" @keyup.enter="addUploadTag" />
            <div class="upload-tip">{{ t('multipleTagsOptional') }}</div>
          </div>
        </el-form-item>
        <el-form-item :label="t('downloadPermission')">
          <el-radio-group v-model="modelUploadForm.downloadPermission">
            <el-radio :value="1">{{ t('allowDownload') }}</el-radio>
            <el-radio :value="0">{{ t('denyDownload') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('publishStatus')">
          <el-tag class="publish-status-tag" type="warning" effect="light">
            {{ t('uploadPendingReview') }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="t('modelFile')" required>
          <el-upload
            class="upload-field"
            action="#"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleUploadModelFileChange"
            :on-remove="handleUploadModelFileRemove"
            accept=".glb"
          >
            <el-button class="upload-trigger" type="primary" plain>{{ t('selectModelFile') }}</el-button>
          </el-upload>
          <div class="upload-tip">{{ t('modelFileTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('thumbnail')">
          <el-upload
            class="upload-field"
            action="#"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleUploadThumbnailFileChange"
            :on-remove="handleUploadThumbnailFileRemove"
            accept=".png,.jpg,.jpeg,.webp"
          >
            <el-button class="upload-trigger" type="primary" plain>{{ t('selectThumbnailOptional') }}</el-button>
          </el-upload>
          <div class="upload-tip">{{ t('thumbnailTip') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelUploadDialogVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" :loading="uploadingModel" @click="submitModelUpload">{{ t('upload') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="chatNoteDialogVisible"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="t('editChatNote')"
      width="600px"
    >
      <el-input
        v-model="chatNoteDraft"
        type="textarea"
        :rows="8"
        :placeholder="t('chatNotePlaceholder')"
      />
      <template #footer>
        <el-button @click="chatNoteDialogVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" :loading="chatNoteSaving" @click="saveChatNoteAction">{{ t('save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { updateUserInfo } from '@/api/auth'
import { Camera, Loading } from '@element-plus/icons-vue'
import {
  getUserFavorites,
  getUserLikes,
  getUserComments,
  getUserChatHistory,
  updateUserChatNote,
  generateUserChatNote,
  deleteUserChatHistory,
  getUserDownloads,
  getUserMaterialPresets,
  deleteMaterialPreset,
  applyContributor
} from '@/api/profile'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUiPreferences } from '@/composables/useUiPreferences'
import { openLoginGuide } from '@/composables/useLoginGuide'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { t, language } = useUiPreferences()

const activeTab = ref('info')

/**
 * 窄屏时标签栏从左侧竖排切到顶部横排。
 * el-tabs 的 tab-position 是 prop 不是 CSS，只能用 JS 跟随视口宽度。
 */
const NARROW_QUERY = '(max-width: 640px)'
const isNarrow = ref(false)
const tabPosition = computed(() => (isNarrow.value ? 'top' : 'left'))

let narrowMedia = null
const onNarrowChange = (e) => {
  isNarrow.value = e.matches
}

onMounted(() => {
  if (typeof window === 'undefined' || !window.matchMedia) return
  narrowMedia = window.matchMedia(NARROW_QUERY)
  isNarrow.value = narrowMedia.matches
  // addEventListener 在老 Safari 上不存在，退回 addListener
  if (narrowMedia.addEventListener) {
    narrowMedia.addEventListener('change', onNarrowChange)
  } else {
    narrowMedia.addListener(onNarrowChange)
  }
})

onUnmounted(() => {
  if (!narrowMedia) return
  if (narrowMedia.removeEventListener) {
    narrowMedia.removeEventListener('change', onNarrowChange)
  } else {
    narrowMedia.removeListener(onNarrowChange)
  }
})

/** 概览统计卡点击后切到对应标签，顺手把地址栏参数也同步，刷新不丢 */
const switchTab = (name) => {
  activeTab.value = name
  router.replace({ path: '/profile', query: { ...route.query, tab: name } })
}

const saving = ref(false)
const changing = ref(false)
const submitting = ref(false)
const contributionDialogVisible = ref(false)
const contributionStatus = ref('')
const pageSize = 10
const defaultImage = 'https://via.placeholder.com/300x200?text=No+Image'
const modelUploadDialogVisible = ref(false)
const uploadingModel = ref(false)
const uploadModelFile = ref(null)
const uploadThumbnailFile = ref(null)
const categoryOptions = ref([])
const uploadTagInput = ref('')
const modelUploadForm = ref({
  modelName: '',
  categoryId: null,
  dynasty: '',
  location: '',
  description: '',
  tags: [],
  downloadPermission: 1
})
const chatNoteDialogVisible = ref(false)
const chatNoteSaving = ref(false)
const chatNoteCurrentId = ref(null)
const chatNoteDraft = ref('')

const userInfo = computed(() => authStore.userInfo || {})

// 头像上传状态
const avatarInputRef = ref(null)
const avatarUploading = ref(false)
const avatarDragOver = ref(false)

const triggerAvatarPick = () => {
  if (avatarUploading.value) return
  avatarInputRef.value?.click()
}

/** 拖拽放下时走和「选择文件」同一套校验，避免规则分叉 */
const handleAvatarDrop = (event) => {
  avatarDragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  uploadAvatarFile(file)
}

const handleAvatarChange = (event) => {
  const file = event.target.files?.[0]
  // 选完立刻清空，否则连续选同一个文件不会再触发 change
  event.target.value = ''
  if (!file) return
  uploadAvatarFile(file)
}

const uploadAvatarFile = async (file) => {
  if (!file.type.startsWith('image/')) {
    ElMessage.error(t('avatarTip'))
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error(t('avatarTip'))
    return
  }

  avatarUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    await request.post('/users/me/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    // 后端只回 URL，全局 userInfo 要重新拉一次，导航栏/评论区头像才会同步
    await authStore.fetchUserInfo()
    ElMessage.success(t('saveSuccess'))
  } catch (error) {
    console.error('[Profile] 头像上传失败', error)
    const msg = error?.response?.data?.message || error?.message || t('saveFailed')
    ElMessage.error(msg)
  } finally {
    avatarUploading.value = false
  }
}

const form = reactive({
  username: '',
  nickname: '',
  email: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const contributionForm = reactive({
  description: ''
})

const passwordFormRef = ref(null)

// 数据列表
const favorites = ref([])
const favoritesPage = ref(1)
const favoritesTotal = ref(0)
const favoritesKeyword = ref('')

const likes = ref([])
const likesPage = ref(1)
const likesTotal = ref(0)
const likesKeyword = ref('')

const comments = ref([])
const commentsPage = ref(1)
const commentsTotal = ref(0)

const chatHistory = ref([])
const chatHistoryPage = ref(1)
const chatHistoryTotal = ref(0)

const downloads = ref([])
const downloadsPage = ref(1)
const downloadsTotal = ref(0)

const materialPresets = ref([])
const autoRefreshTimer = ref(null)

const pendingModels = ref([])
const pendingModelsPage = ref(1)
const pendingModelsTotal = ref(0)

const rejectedModels = ref([])
const rejectedModelsPage = ref(1)
const rejectedModelsTotal = ref(0)

const filteredFavorites = computed(() => {
  const keyword = favoritesKeyword.value.trim().toLowerCase()
  if (!keyword) return favorites.value
  return favorites.value.filter(item =>
    `${item.modelName || ''}${item.dynasty || ''}${item.location || ''}`.toLowerCase().includes(keyword)
  )
})

const filteredLikes = computed(() => {
  const keyword = likesKeyword.value.trim().toLowerCase()
  if (!keyword) return likes.value
  return likes.value.filter(item =>
    `${item.modelName || ''}${item.dynasty || ''}${item.location || ''}`.toLowerCase().includes(keyword)
  )
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error(t('passwordMismatch')))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [
    { required: true, message: t('enterOldPassword'), trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: t('enterNewPassword'), trigger: 'blur' },
    { min: 6, message: t('passwordMinLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('enterConfirmPassword'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const getRoleType = (role) => {
  switch (role) {
    case 'admin': return 'danger'
    case 'contributor': return 'success'
    default: return 'info'
  }
}

const getRoleText = (role) => {
  switch (role) {
    case 'admin': return language.value === 'en' ? 'Admin' : '管理员'
    case 'contributor': return language.value === 'en' ? 'Contributor' : '贡献者'
    default: return language.value === 'en' ? 'User' : '普通用户'
  }
}

onMounted(() => {
  /*
    未登录不发起任何数据请求。
    个人中心的每个接口都要登录，游客（或 token 已失效的访问者）进来时
    会并发触发一串 401/403；拦截器会弹登录引导，这里直接不发请求，
    页面保持空态即可。带失效 token 的情况由请求拦截器统一兜底。
  */
  if (!authStore.token) {
    openLoginGuide('修改资料')
    return
  }
  if (userInfo.value) {
    form.username = userInfo.value.username || ''
    form.nickname = userInfo.value.nickname || ''
    form.email = userInfo.value.email || ''
  }
  loadContributionStatus()
  loadCategories()
  if (route.query?.action === 'uploadModel') {
    openModelUploadDialog()
    router.replace({ path: route.path, query: { ...route.query, action: undefined } })
  }
  window.addEventListener('focus', handleWindowFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  const queryTab = route.query?.tab
  if (typeof queryTab === 'string' && queryTab) {
    activeTab.value = queryTab
  }
})

onUnmounted(() => {
  clearAutoRefresh()
  window.removeEventListener('focus', handleWindowFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

const loadContributionStatus = async () => {
  try {
    const response = await request({
      url: '/contribution-applies/my',
      method: 'get'
    })
    if (response.data.data) {
      contributionStatus.value = response.data.data.status
    }
  } catch (error) {
    console.error('[Profile] 获取申请状态失败', error)
  }
}

const loadCategories = async () => {
  try {
    const response = await request.get('/categories')
    categoryOptions.value = response.data?.data || []
  } catch (error) {
    console.error('[Profile] 加载分类失败', error)
  }
}

const openModelUploadDialog = () => {
  modelUploadForm.value = {
    modelName: '',
    categoryId: null,
    dynasty: '',
    location: '',
    description: '',
    tags: [],
    downloadPermission: 1
  }
  uploadModelFile.value = null
  uploadThumbnailFile.value = null
  uploadTagInput.value = ''
  modelUploadDialogVisible.value = true
}

const handleUploadModelFileChange = (file) => {
  const fileName = (file.name || '').toLowerCase()
  if (!fileName.endsWith('.glb')) {
    ElMessage.warning(t('modelFileTip'))
    uploadModelFile.value = null
    return false
  }
  uploadModelFile.value = file.raw
  return true
}

const handleUploadModelFileRemove = () => {
  uploadModelFile.value = null
}

const handleUploadThumbnailFileChange = (file) => {
  const fileName = (file.name || '').toLowerCase()
  const isSupported = fileName.endsWith('.png') || fileName.endsWith('.jpg')
    || fileName.endsWith('.jpeg') || fileName.endsWith('.webp')
  if (!isSupported) {
    ElMessage.warning(t('thumbnailTip'))
    uploadThumbnailFile.value = null
    return false
  }
  uploadThumbnailFile.value = file.raw
  return true
}

const handleUploadThumbnailFileRemove = () => {
  uploadThumbnailFile.value = null
}

const addUploadTag = () => {
  const value = uploadTagInput.value.trim()
  if (!value) return
  if (modelUploadForm.value.tags.includes(value)) {
    ElMessage.warning(t('tagAlreadyExists'))
    return
  }
  modelUploadForm.value.tags.push(value)
  uploadTagInput.value = ''
}

const removeUploadTag = (index) => {
  modelUploadForm.value.tags.splice(index, 1)
}

const submitModelUpload = async () => {
  if (!modelUploadForm.value.modelName.trim()) {
    ElMessage.warning(t('enterModelName'))
    return
  }
  if (!uploadModelFile.value) {
    ElMessage.warning(t('modelFileTip'))
    return
  }

  const formData = new FormData()
  formData.append('modelFile', uploadModelFile.value)
  formData.append('modelName', modelUploadForm.value.modelName.trim())
  if (modelUploadForm.value.categoryId) formData.append('categoryId', String(modelUploadForm.value.categoryId))
  if (modelUploadForm.value.dynasty) formData.append('dynasty', modelUploadForm.value.dynasty)
  if (modelUploadForm.value.location) formData.append('location', modelUploadForm.value.location)
  if (modelUploadForm.value.description?.trim()) {
    formData.append('description', modelUploadForm.value.description.trim())
  }
  if (modelUploadForm.value.tags.length > 0) {
    formData.append('tags', JSON.stringify(modelUploadForm.value.tags))
  }
  formData.append('downloadPermission', String(modelUploadForm.value.downloadPermission))
  if (uploadThumbnailFile.value) {
    formData.append('thumbnailFile', uploadThumbnailFile.value)
  }

  uploadingModel.value = true
  try {
    const response = await request.post('/models', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const payload = response.data?.data ?? response.data
    if (payload?.status === 0) {
      ElMessage.success(t('modelUploadSuccessPending'))
    } else {
      ElMessage.success(t('modelUploadSuccess'))
    }
    modelUploadDialogVisible.value = false
    if (activeTab.value === 'myPendingModels') {
      loadPendingModels(pendingModelsPage.value)
    }
  } catch (error) {
    console.error('[Profile] 模型上传失败', error)
    const backendMessage = error?.response?.data?.message
      || error?.response?.data?.error
      || error?.message
    ElMessage.error(backendMessage || t('modelUploadFailed'))
  } finally {
    uploadingModel.value = false
  }
}

const loadPendingModels = async (page = 1) => {
  try {
    const response = await request.get('/models', { params: { status: 0, page, pageSize } })
    const payload = response.data?.data ?? response.data
    pendingModels.value = payload?.records || payload?.content || []
    pendingModelsTotal.value = payload?.total ?? payload?.totalElements ?? 0
  } catch (error) {
    console.error('[Profile] 获取待审模型失败', error)
  }
}

const loadRejectedModels = async (page = 1) => {
  try {
    const response = await request.get('/models', { params: { status: 2, page, pageSize } })
    const payload = response.data?.data ?? response.data
    rejectedModels.value = payload?.records || payload?.content || []
    rejectedModelsTotal.value = payload?.total ?? payload?.totalElements ?? 0
  } catch (error) {
    console.error('[Profile] 获取驳回模型失败', error)
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await updateUserInfo({
      nickname: form.nickname,
      email: form.email
    })
    await authStore.fetchUserInfo()
    ElMessage.success(t('saveSuccess'))
  } catch (error) {
    ElMessage.error(t('saveFailed'))
  } finally {
    saving.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      changing.value = true
      try {
        await request({
          url: '/users/password',
          method: 'put',
          data: {
            oldPassword: passwordForm.oldPassword,
            newPassword: passwordForm.newPassword
          }
        })
        ElMessage.success(t('passwordChangedSuccess'))
        passwordFormRef.value.resetFields()
      } catch (error) {
        ElMessage.error(t('updateFailed'))
      } finally {
        changing.value = false
      }
    }
  })
}

const showContributionDialog = () => {
  contributionDialogVisible.value = true
  contributionForm.description = ''
}

const submitContribution = async () => {
  if (!contributionForm.description) {
    ElMessage.warning(t('fillApplicationDescription'))
    return
  }

  submitting.value = true
  try {
    await applyContributor({
      description: contributionForm.description
    })
    ElMessage.success(t('applicationSubmitted'))
    contributionDialogVisible.value = false
    contributionStatus.value = 'pending'
  } catch (error) {
    ElMessage.error(t('submitFailed'))
  } finally {
    submitting.value = false
  }
}

const loadFavorites = async (page = 1) => {
  try {
    const response = await getUserFavorites({ page, pageSize })
    const records = response.data.data.records || []
    favorites.value = records.map(item => ({
      ...item,
      modelId: item.modelId || item.id,
      thumbnailPath: item.thumbnailPath || '',
      categoryName: item.categoryName || '',
      dynasty: item.dynasty || '',
      location: item.location || '',
      viewCount: item.viewCount || 0,
      likeCount: item.likeCount || 0,
      downloadCount: item.downloadCount || 0
    }))
    favoritesTotal.value = response.data.data.total || 0
  } catch (error) {
    console.error('[Profile] 获取收藏失败', error)
  }
}

const loadLikes = async (page = 1) => {
  try {
    const response = await getUserLikes({ page, pageSize })
    const records = response.data.data.records || []
    likes.value = records.map(item => ({
      ...item,
      modelId: item.modelId || item.id,
      thumbnailPath: item.thumbnailPath || '',
      categoryName: item.categoryName || '',
      dynasty: item.dynasty || '',
      location: item.location || '',
      viewCount: item.viewCount || 0,
      likeCount: item.likeCount || 0,
      downloadCount: item.downloadCount || 0
    }))
    likesTotal.value = response.data.data.total || 0
  } catch (error) {
    console.error('[Profile] 获取点赞失败', error)
  }
}

const loadComments = async (page = 1) => {
  try {
    const response = await getUserComments({ page, pageSize })
    comments.value = response.data.data.records || []
    commentsTotal.value = response.data.data.total || 0
  } catch (error) {
    console.error('[Profile] 获取评论失败', error)
  }
}

const loadChatHistory = async (page = 1) => {
  try {
    const response = await getUserChatHistory({ page, pageSize })
    const records = response.data.data.records || []
    chatHistory.value = records.map(item => ({
      ...item,
      userQuestion: item.userQuestion || item.question || '',
      aiAnswer: item.aiAnswer || item.answer || '',
      createdAt: item.createdAt || item.createTime || '',
      modelName: item.modelName || '',
      modelId: item.modelId || null,
      sourceType: item.sourceType || 'model-chat',
      sourceLabel: item.sourceLabel || '模型聊天',
      tokensUsed: item.tokensUsed || 0,
      chatNote: item.chatNote || ''
    }))
    chatHistoryTotal.value = response.data.data.total || 0
  } catch (error) {
    console.error('[Profile] 获取问答历史失败', error)
  }
}

const openChatNoteEditor = (item) => {
  chatNoteCurrentId.value = item.id
  chatNoteDraft.value = item.chatNote || ''
  chatNoteDialogVisible.value = true
}

const saveChatNoteAction = async () => {
  if (!chatNoteCurrentId.value) return
  chatNoteSaving.value = true
  try {
    await updateUserChatNote(chatNoteCurrentId.value, chatNoteDraft.value)
    ElMessage.success(t('noteSaved'))
    chatNoteDialogVisible.value = false
    loadChatHistory(chatHistoryPage.value)
  } catch (error) {
    console.error('[Profile] 保存备注失败', error)
    ElMessage.error(t('noteSaveFailed'))
  } finally {
    chatNoteSaving.value = false
  }
}

const generateChatNoteAction = async (item) => {
  try {
    await generateUserChatNote(item.id)
    ElMessage.success(t('noteGenerated'))
    loadChatHistory(chatHistoryPage.value)
  } catch (error) {
    console.error('[Profile] 生成笔记失败', error)
    ElMessage.error(t('noteGenerateFailed'))
  }
}

const deleteChatHistoryAction = async (id) => {
  try {
    await deleteUserChatHistory(id)
    ElMessage.success(t('chatHistoryDeleted'))
    loadChatHistory(chatHistoryPage.value)
  } catch (error) {
    console.error('[Profile] 删除问答记录失败', error)
    ElMessage.error(t('actionFailed'))
  }
}

const loadDownloads = async (page = 1) => {
  try {
    const response = await getUserDownloads({ page, pageSize })
    downloads.value = response.data.data.records || []
    downloadsTotal.value = response.data.data.total || 0
  } catch (error) {
    console.error('[Profile] 获取下载记录失败', error)
  }
}

const loadMaterialPresets = async () => {
  try {
    const response = await getUserMaterialPresets({ page: 1, pageSize: 50 })
    materialPresets.value = response.data.data.records || []
  } catch (error) {
    console.error('[Profile] 获取材质方案失败', error)
  }
}

const refreshCurrentTabData = () => {
  switch (activeTab.value) {
    case 'chatHistory':
      loadChatHistory(chatHistoryPage.value)
      break
    case 'materialPresets':
      loadMaterialPresets()
      break
    default:
      break
  }
}

const clearAutoRefresh = () => {
  if (autoRefreshTimer.value) {
    window.clearInterval(autoRefreshTimer.value)
    autoRefreshTimer.value = null
  }
}

const setupAutoRefresh = () => {
  clearAutoRefresh()
  if (activeTab.value === 'chatHistory' || activeTab.value === 'materialPresets') {
    autoRefreshTimer.value = window.setInterval(() => {
      refreshCurrentTabData()
    }, 10000)
  }
}

const handleWindowFocus = () => {
  refreshCurrentTabData()
}

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') {
    refreshCurrentTabData()
  }
}

const removeFavorite = async (modelId) => {
  try {
    await request({
      url: `/models/${modelId}/favorite`,
      method: 'delete'
    })
    ElMessage.success(t('removeFavoriteSuccess'))
    loadFavorites(favoritesPage.value)
  } catch (error) {
    ElMessage.error(t('removeFavoriteFailed'))
  }
}

const removeLike = async (modelId) => {
  try {
    await request({
      // 后端是 toggle like：POST 即可取消/点赞
      url: `/models/${modelId}/like`,
      method: 'post'
    })
    ElMessage.success(t('removeLikeSuccess'))
    loadLikes(likesPage.value)
  } catch (error) {
    ElMessage.error(t('removeLikeFailed'))
  }
}

const deleteComment = async (commentId) => {
  try {
    await request({
      url: `/comments/${commentId}`,
      method: 'delete'
    })
    ElMessage.success(t('commentDeleted'))
    loadComments(commentsPage.value)
  } catch (error) {
    ElMessage.error(t('commentDeleteFailed'))
  }
}

const deleteMaterialPresetAction = async (presetId) => {
  try {
    await deleteMaterialPreset(presetId)
    ElMessage.success(t('materialPresetDeleted'))
    materialPresets.value = materialPresets.value.filter(p => p.id !== presetId)
  } catch (error) {
    ElMessage.error(t('materialPresetDeleteFailed'))
  }
}

const applyMaterialPreset = (preset) => {
  ElMessage.success(`${t('materialPresetApplied')}: ${preset.presetName}`)
  router.push(`/viewer/${preset.modelId}?preset=${preset.id}`)
}

const goToDetail = (modelId) => {
  router.push(`/buildings/${modelId}`)
}

// 监听标签页切换，加载对应数据
watch(activeTab, (newTab) => {
  switch (newTab) {
    case 'favorites':
      loadFavorites(favoritesPage.value)
      break
    case 'likes':
      loadLikes(likesPage.value)
      break
    case 'comments':
      loadComments(commentsPage.value)
      break
    case 'chatHistory':
      loadChatHistory(chatHistoryPage.value)
      break
    case 'downloads':
      loadDownloads(downloadsPage.value)
      break
    case 'materialPresets':
      loadMaterialPresets()
      break
    case 'myPendingModels':
      loadPendingModels(pendingModelsPage.value)
      break
    case 'myRejectedModels':
      loadRejectedModels(rejectedModelsPage.value)
      break
  }
  setupAutoRefresh()
})

watch(
  () => route.query?.tab,
  (newTab) => {
    if (typeof newTab === 'string' && newTab && activeTab.value !== newTab) {
      activeTab.value = newTab
    }
  }
)
</script>

<style lang="scss" scoped>
.profile-container {
  max-width: 1000px;
  margin: 0 auto;

  :deep(.publish-status-tag) {
    display: inline-flex;
    align-items: center;
    height: 32px;
    padding: 0 14px;
    border-radius: 8px;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.2px;
    color: var(--v2-chip-text);
    background: var(--v2-chip-bg);
    border: 1px solid var(--v2-chip-border);
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
  }

  .profile-header {
    display: flex;
    align-items: center;
    gap: 20px;
    flex-wrap: wrap;
    background: var(--v2-surface-strong);
    border: 1px solid var(--v2-border-soft);
    padding: 30px;
    border-radius: var(--v2-radius-lg);
    margin-bottom: 20px;
    box-shadow: var(--v2-shadow-soft);

    // 概览统计：贴右侧，窄屏自动换到下一行
    .profile-stats {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-left: auto;

      .stat-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 2px;
        min-width: 72px;
        padding: 8px 12px;
        border-radius: 10px;
        cursor: pointer;
        transition: background 180ms ease, transform 180ms ease;

        &:hover {
          background: var(--v2-surface-hover);
          transform: translateY(-2px);
        }

        .stat-value {
          font-size: 20px;
          font-weight: 700;
          color: var(--v2-text-strong);
          font-variant-numeric: tabular-nums;
          line-height: 1.1;
        }

        .stat-label {
          font-size: 12px;
          color: var(--v2-text-weak);
          white-space: nowrap;
        }
      }
    }

    // 头像外框只负责定位与悬停反馈，实际头像还是 el-avatar 自己画
    .avatar-wrap {
      position: relative;
      width: 80px;
      height: 80px;
      border-radius: 50%;
      cursor: pointer;
      flex: none;
      overflow: hidden;
      box-shadow: 0 4px 14px rgba(15, 23, 42, 0.12);

      :deep(.el-avatar) {
        width: 100%;
        height: 100%;
      }

      .avatar-mask {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 2px;
        border-radius: 50%;
        background: color-mix(in srgb, var(--v2-bg-0) 62%, transparent);
        color: var(--v2-text-strong);
        font-size: 12px;
        line-height: 1.2;
        opacity: 0;
        transition: opacity 180ms ease;

        .el-icon {
          font-size: 20px;
        }
      }

      &:hover .avatar-mask,
      &.uploading .avatar-mask {
        opacity: 1;
      }

      // 拖拽悬停时高亮，明确告诉用户这里可以放
      &.is-dragover {
        box-shadow: 0 0 0 3px var(--v2-chip-border), 0 0 20px color-mix(in srgb, var(--v2-gold) 40%, transparent);

        .avatar-mask {
          opacity: 1;
          background: color-mix(in srgb, var(--v2-gold) 62%, transparent);
        }
      }

      &.uploading {
        cursor: progress;
      }

      .is-loading-icon {
        animation: avatar-spin 900ms linear infinite;
      }

      // 原生 file input 藏起来，只留一个视觉可点的头像
      .avatar-input {
        display: none;
      }
    }

    .user-info {
      margin-left: 20px;

      h2 {
        font-size: 24px;
        color: var(--v2-text-strong);
        margin-bottom: 10px;
      }

      p {
        color: var(--v2-text-weak);
        font-size: 14px;
        margin-bottom: 10px;
      }

      .user-tags {
        display: flex;
        gap: 10px;
        align-items: center;

        :deep(.el-tag) {
          color: var(--v2-chip-text) !important;
          background: var(--v2-chip-bg) !important;
          border-color: var(--v2-chip-border) !important;
          font-weight: 700;
          letter-spacing: 0.2px;
          text-shadow: none !important;
          filter: none !important;
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
          -webkit-font-smoothing: antialiased;
          -moz-osx-font-smoothing: grayscale;
        }

        :deep(.el-tag.el-tag--danger) {
          color: var(--v2-danger-text) !important;
          background: var(--v2-danger-bg) !important;
          border-color: var(--v2-danger-border) !important;
        }

        :deep(.el-button) {
          color: var(--v2-text-body) !important;
          border-color: var(--v2-border-soft) !important;
          background: var(--v2-surface-soft) !important;
          text-shadow: none !important;
          filter: none !important;
          backdrop-filter: none !important;
          -webkit-backdrop-filter: none !important;
          -webkit-font-smoothing: antialiased;
          -moz-osx-font-smoothing: grayscale;
        }

        :deep(.el-button:hover) {
          color: var(--v2-text-strong) !important;
          background: var(--v2-surface-hover) !important;
          border-color: var(--v2-gold) !important;
          box-shadow: none !important;
        }
      }
    }
  }

  .profile-tabs {
    background: var(--v2-surface-strong);
    border: 1px solid var(--v2-border-soft);
    padding: 20px;
    border-radius: var(--v2-radius-lg);
    box-shadow: var(--v2-shadow-soft);

    // 标签改为左侧竖排：原来 8-9 个标签横向挤在一行要左右滚动，
    // 竖排后每项一行，一眼能看全，也方便按用途分组阅读
    :deep(.el-tabs__header.is-left) {
      margin-right: 20px;
      min-width: 140px;
    }

    :deep(.el-tabs__item.is-left) {
      justify-content: flex-start;
      height: 42px;
      font-size: 13px;
      padding: 0 16px;
      border-radius: 8px;
      margin-bottom: 4px;
      color: var(--v2-text-body);

      &:hover {
        background: var(--v2-surface-soft);
        color: var(--v2-text-strong);
      }

      &.is-active {
        color: var(--v2-chip-text);
        background: var(--v2-chip-bg);
        font-weight: 600;
      }
    }

    :deep(.el-tabs__active-bar.is-left) {
      border-radius: 2px;
      background-color: var(--v2-gold);
    }

    .profile-form {
      max-width: 500px;
      margin-top: 20px;
    }

    .empty-state {
      padding: 40px 0;
    }

    .tab-header-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
    }

    .tab-search-input {
      width: 280px;
    }

    .chat-history-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .model-card-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 16px;
    }

    .model-card {
      background: var(--v2-surface);
      border-radius: var(--v2-radius-md);
      overflow: hidden;
      border: 1px solid var(--v2-border-soft);
      transition: transform 0.3s, box-shadow 0.3s;

      &:hover {
        transform: translateY(-4px);
        box-shadow: var(--v2-shadow-soft);

        .card-overlay {
          opacity: 1;
        }
      }

      .card-image {
        position: relative;
        height: 160px;
        cursor: pointer;

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .card-overlay {
          position: absolute;
          inset: 0;
          background: color-mix(in srgb, var(--v2-bg-0) 52%, transparent);
          display: flex;
          align-items: center;
          justify-content: center;
          opacity: 0;
          transition: opacity 0.25s;
        }

        .view-btn {
          color: var(--v2-text-strong);
          border: 1px solid var(--v2-chip-border);
          background: color-mix(in srgb, var(--v2-bg-0) 42%, transparent);
          border-radius: 16px;
          padding: 6px 14px;
          font-size: 12px;
        }

        .card-badges {
          position: absolute;
          left: 8px;
          top: 8px;
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
        }
      }

      .card-content {
        padding: 12px;

        .model-name {
          margin: 0 0 6px;
          font-size: 15px;
          color: var(--v2-text-strong);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .dynasty {
          margin: 0 0 4px;
          color: var(--v2-gold);
          font-size: 13px;
        }

        .location {
          margin: 0 0 8px;
          color: var(--v2-text-weak);
          font-size: 12px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .reject-reason {
          margin: 0 0 8px;
          color: var(--v2-danger-text);
          font-size: 12px;
          line-height: 1.4;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .card-stats {
          display: flex;
          gap: 10px;
          color: var(--v2-text-weak);
          font-size: 12px;
          margin-bottom: 10px;
        }

        .card-actions {
          display: flex;
          justify-content: flex-end;
        }
      }
    }

    .el-pagination {
      margin-top: 20px;
      justify-content: center;
    }

    .list-content {
      .list-item {
        display: flex;
        justify-content: flex-start;
        align-items: center;
        padding: 15px 0;
        border-bottom: 1px solid var(--v2-divider);
        cursor: pointer;
        gap: 12px;

        &:last-child {
          border-bottom: none;
        }

        .item-thumb {
          width: 84px;
          height: 56px;
          border-radius: 6px;
          overflow: hidden;
          flex-shrink: 0;

          img {
            width: 100%;
            height: 100%;
            object-fit: cover;
          }
        }

        .item-info {
          flex: 1;

          .item-title {
            font-size: 16px;
            color: var(--v2-text-strong);
            margin-bottom: 5px;
            cursor: pointer;

            &:hover {
              color: var(--v2-gold);
            }
          }

          .item-content {
            font-size: 14px;
            color: var(--v2-text-body);
            margin: 5px 0;
          }

          .item-meta {
            font-size: 12px;
            color: var(--v2-text-weak);
          }

          .item-question {
            font-size: 14px;
            color: var(--v2-text-strong);
            margin-bottom: 5px;
          }

          .item-source {
            margin-bottom: 6px;
          }

          .item-answer {
            font-size: 14px;
            color: var(--v2-text-body);
            margin-bottom: 5px;
            overflow: hidden;
            text-overflow: ellipsis;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
          }

          .chat-note {
            margin-top: 8px;
            padding: 8px 10px;
            border-radius: 6px;
            background: var(--v2-surface-soft);
            border-left: 3px solid var(--v2-gold);

            .note-label {
              font-size: 12px;
              color: var(--v2-text-weak);
              margin-bottom: 4px;
            }

            .note-content {
              font-size: 13px;
              color: var(--v2-text-body);
              white-space: pre-wrap;
              line-height: 1.6;
            }
          }
        }

        .item-actions {
          display: flex;
          gap: 10px;
        }
      }
    }



    /*
      平板：头部统计从右侧换行到下方整宽，避免被挤出容器；
      左侧标签收窄，给表单留出更多空间。
    */

    /*
      手机：标签栏由左侧竖排改为顶部横排并允许横向滚动。
      竖排侧栏在 375px 下会吃掉近 40% 宽度，右侧表单被压到看不清。
    */
  }
    @media (max-width: 900px) {
      .model-card-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .tab-search-input {
        width: 220px;
      }
    }

    @media (max-width: 1024px) {
      .profile-header {
        flex-wrap: wrap;
        padding: 20px;

        .profile-stats {
          width: 100%;
          margin-left: 0;
          margin-top: 14px;
          justify-content: space-around;

          .stat-item {
            min-width: 0;
            flex: 1;
            padding: 6px 4px;
          }
        }
      }

      .profile-tabs {
        :deep(.el-tabs__header.is-left) {
          min-width: 108px;
          margin-right: 12px;
        }
      }
    }

    @media (max-width: 640px) {
      .profile-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 12px;
        padding: 16px;

        .avatar-wrap {
          width: 64px;
          height: 64px;
        }

        .user-info {
          margin-left: 0;
        }

        .profile-stats {
          margin-top: 4px;
          margin-left: 0;
          width: 100%;
          justify-content: space-between;
          gap: 0;
          // 四项必须挤在一行，否则会换行后溢出容器
          flex-wrap: nowrap;
          overflow: hidden;

          .stat-item {
            /* 覆盖基础规则的 min-width:72px —— 四个 72px 在 375px 屏上放不下 */
            min-width: 0;
            flex: 1 1 0;
            padding: 6px 0;

            .stat-value {
              font-size: 17px;
            }

            .stat-label {
              font-size: 11px;
              // 标签可能被压到换行，限制成单行省略
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
              max-width: 100%;
            }
          }
        }
      }

      .profile-tabs {
        padding: 12px;

        // el-tabs 在窄屏切换成顶部标签，横向滚动而不是换行
        :deep(.el-tabs__header.is-left) {
          min-width: 0;
          margin-right: 0;
        }

        :deep(.el-tabs__nav-scroll) {
          overflow-x: auto;
        }

        .profile-form {
          margin-top: 12px;
        }

        .model-card-grid {
          grid-template-columns: 1fr;
        }

        .tab-header-row {
          flex-wrap: wrap;
        }

        .tab-search-input {
          width: 100%;
        }
      }
    }


    @media (max-width: 1200px) {
      .model-card-grid {
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }
    }
}

@keyframes avatar-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
