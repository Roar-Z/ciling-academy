<template>
  <div class="page-container profile-page">
    <!-- 身份卡：Editorial 风，顶部细线 + 头像 + 座右铭 -->
    <div class="profile-hero ws-card">
      <div class="hero-cover"></div>
      <div class="hero-body">
        <div class="hero-top">
          <div class="hero-id">
            <div class="hero-avatar-wrap" @click="openEditProfile">
              <el-avatar :size="76" :src="userStore.userInfo?.avatar || ''" class="hero-avatar">
                {{ avatarText }}
              </el-avatar>
              <el-tooltip
                placement="bottom"
                :show-after="120"
                :offset="6"
                effect="light"
                popper-class="lv-tip-popper"
              >
                <template #content>
                  <div class="lv-tip">
                    <div class="lv-tip-head">
                      <b>Lv.{{ level }}</b>
                      <span v-if="lvInfo">{{ lvInfo.titleIcon }} {{ lvInfo.title }}</span>
                    </div>
                    <div class="lv-tip-bar">
                      <i :style="{ width: `${levelPercent}%` }"></i>
                    </div>
                    <div class="lv-tip-foot">
                      {{ lvInfo ? (lvInfo.isMax ? '已达满级，感谢热爱' : `还差 ${lvInfo.remainExp} 成长值升级`) : '点击查看等级详情' }}
                    </div>
                  </div>
                </template>
                <span
                  class="hero-level-badge"
                  :class="{ 'has-reward': lvInfo?.hasClaimable }"
                  @click.stop="openLevelDialog"
                >
                  Lv.{{ level }}
                  <i v-if="lvInfo?.hasClaimable" class="lv-badge-dot"></i>
                </span>
              </el-tooltip>
            </div>
            <div class="hero-meta">
              <div class="hero-greet">{{ todayGreet }}，{{ userStore.userInfo?.nickname || '同学' }}</div>
              <div class="hero-name-row">
                <span class="hero-username">@{{ userStore.userInfo?.username }}</span>
                <span v-if="heroTag" class="hero-tag">
                  <AppIcon
                    v-if="heroTagIcon"
                    :name="heroTagIcon"
                    :size="14"
                    color="#3D9A7E"
                    class="hero-tag-icon"
                  />
                  {{ heroTag }}
                </span>
              </div>
              <p class="hero-joined">词灵学园学员 · 加入于 {{ joinDate }}</p>
            </div>
          </div>
          <el-button size="small" plain class="hero-edit" @click="openEditProfile">
            <AppIcon name="pencil" :size="13" />
            编辑资料
          </el-button>
        </div>
        <p v-if="userStore.userInfo?.bio" class="hero-quote">{{ userStore.userInfo.bio }}</p>
        <p v-else class="hero-quote placeholder">写下你的座右铭，激励每一天的学习</p>
      </div>
    </div>

    <!-- 数据叙事：今日学习 + CET 倒计时（双栏，1px 线分隔） -->
    <div class="metric-row">
      <div class="metric-card ws-card">
        <div class="metric-label">
          今日学习
        </div>
        <div class="metric-num">
          {{ userStore.userInfo?.todayWords ?? 0 }}<span class="metric-unit">词</span>
        </div>
        <div class="metric-foot">
          累计 <b>{{ userStore.userInfo?.totalWords ?? 0 }}</b> 词 ·
          目标 <b>{{ dailyGoal }}</b>/天
        </div>
        <div class="metric-bar">
          <div class="metric-bar-fill" :style="{ width: `${todayPercent}%` }"></div>
        </div>
      </div>

      <div class="metric-card ws-card cet">
        <div class="metric-head">
          <div class="metric-label">距 CET-{{ cetLevel }} 考试</div>
          <div class="cet-tabs">
            <span :class="['cet-tab', { active: cetLevel === 4 }]" @click="setCetLevel(4)">四级</span>
            <span :class="['cet-tab', { active: cetLevel === 6 }]" @click="setCetLevel(6)">六级</span>
          </div>
        </div>
        <div class="metric-num">
          {{ cetInfo.days }}<span class="metric-unit">天</span>
        </div>
        <div class="metric-foot">{{ cetInfo.dateText }}</div>
        <div class="cet-clock">
          {{ pad2(cetInfo.hours) }}:{{ pad2(cetInfo.minutes) }}:{{ pad2(cetInfo.seconds) }}
        </div>
      </div>
    </div>

    <!-- 数据概览：极简细线网格 -->
    <div class="stats-grid">
      <div v-for="s in statCards" :key="s.label" class="stat-cell">
        <div class="stat-num">{{ s.value }}</div>
        <div class="stat-label">{{ s.label }}</div>
      </div>
    </div>

    <!-- 中部两栏 -->
    <div class="profile-main">
      <!-- 左侧：学习进度 + 成就 -->
      <div class="profile-col">
        <!-- 今日目标 -->
        <div class="ws-card section-card">
          <div class="section-head">
            <AppIcon name="target" :size="18" />
            <h3>今日目标</h3>
            <span class="section-count">{{ todayPercent }}%</span>
          </div>
          <div class="goal-info">
            <div class="goal-title">
              今日已学
              <b>{{ userStore.userInfo?.todayWords ?? 0 }}</b>
              <span class="goal-sep">/</span>
              <el-popover
                v-model:visible="goalPopoverVisible"
                placement="bottom-start"
                :width="280"
                trigger="click"
              >
                <template #reference>
                  <span class="goal-num editable" @click="openGoalPopover">
                    {{ dailyGoal }}
                  </span>
                </template>
                <div class="goal-popover">
                  <div class="goal-popover-title">设置每日学习目标</div>
                  <div class="goal-presets">
                    <span
                      v-for="p in [10, 20, 30, 50]"
                      :key="p"
                      :class="['goal-preset', { active: goalDraft === p }]"
                      @click="goalDraft = p"
                    >{{ p }} 词</span>
                  </div>
                  <el-input-number
                    v-model="goalDraft"
                    :min="5"
                    :max="200"
                    :step="5"
                    size="small"
                    controls-position="right"
                    class="goal-input"
                  />
                  <div class="goal-popover-foot">
                    <el-button size="small" plain @click="goalPopoverVisible = false">取消</el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :loading="goalSaving"
                      @click="confirmDailyGoal"
                    >保存</el-button>
                  </div>
                </div>
              </el-popover>
              词
            </div>
            <div class="goal-bar">
              <div class="goal-bar-fill" :style="{ width: `${todayPercent}%` }"></div>
            </div>
            <p class="goal-tip">
              {{ todayPercent >= 100 ? '目标已达成，继续保持' : '再学几个单词即可完成今日目标' }}
              <span class="goal-link" @click="go('/review')">去背单词 →</span>
            </p>
          </div>
        </div>

        <!-- 备考资料 -->
        <div class="panel ws-card resources-panel">
          <div class="section-head">
            <div class="section-title">
              <AppIcon name="book-open" :size="18" />
              <h3>备考资料</h3>
            </div>
            <div class="res-tabs">
              <button
                v-for="t in resTabs"
                :key="t.key"
                class="res-tab"
                :class="{ active: resTab === t.key }"
                @click="resTab = t.key"
              >
                {{ t.label }}
              </button>
            </div>
          </div>
          <div class="res-list">
            <div v-for="r in pagedResources" :key="r.item.id" class="res-item" @click="onResourceClick(r)">
              <span class="res-item-icon" :style="{ background: resMeta(r).color + '1A' }">
                <AppIcon :name="resMeta(r).icon" :size="16" :color="resMeta(r).color" />
              </span>
              <span class="res-name">{{ r.item.name }}</span>
              <span v-if="r.owned" class="res-owned">已拥有</span>
              <span v-else class="res-price">{{ r.item.price }} 金币</span>
              <AppIcon name="chevron-right" :size="14" color="#C4CFC9" />
            </div>
            <div v-if="!filteredResources.length" class="res-empty">该分类下暂无资料</div>
          </div>
          <div v-if="resTotalPages > 1" class="res-pager">
            <button class="res-page-btn" :disabled="resPage === 1" aria-label="上一页" @click="resPage--">
              <AppIcon name="chevron-left" :size="15" />
            </button>
            <span class="res-page-ind">{{ resPage }} / {{ resTotalPages }}</span>
            <button class="res-page-btn" :disabled="resPage === resTotalPages" aria-label="下一页" @click="resPage++">
              <AppIcon name="chevron-right" :size="15" />
            </button>
          </div>
        </div>

        <!-- 成就徽章 -->
        <div class="ws-card section-card">
          <div class="section-head">
            <AppIcon name="award" :size="18" />
            <h3>成就徽章</h3>
            <span class="ach-points" v-if="ach">{{ ach.unlockedPoints }}<i>/{{ ach.totalPoints }} 分</i></span>
            <span class="section-count">{{ ach ? `${ach.unlockedCount}/${ach.totalCount}` : '…' }}</span>
          </div>
          <div class="badge-grid">
            <div
              v-for="b in visibleAchievements"
              :key="b.code"
              class="badge-item"
              :class="{ unlocked: b.unlocked }"
            >
              <div class="badge-icon">
                <AppIcon :name="b.unlocked ? b.icon : 'lock'" :size="18" />
              </div>
              <div class="badge-text">
                <div class="badge-name">{{ b.name }}</div>
                <div class="badge-desc" v-if="b.unlocked">{{ formatAchDate(b.unlockedAt) }} 达成</div>
                <div class="badge-desc" v-else>{{ b.desc }}</div>
              </div>
              <span class="badge-points">{{ b.points }} 分</span>
            </div>
          </div>
          <!-- 查看全部：简约文字按钮，悬浮主色高亮 -->
          <button class="badge-more" @click="router.push('/achievements')">
            <span>查看全部 {{ ach?.totalCount || 0 }} 项成就</span>
            <AppIcon name="chevron-right" :size="14" />
          </button>
        </div>
      </div>

      <!-- 右侧：我的装扮 + 账号安全 + 快捷入口 -->
      <div class="profile-col">
        <!-- 我的装扮 -->
        <div class="ws-card section-card">
          <div class="section-head">
            <AppIcon name="palette" :size="18" />
            <h3>我的装扮</h3>
            <span v-if="myGoodsList.length" class="section-count">{{ myGoodsList.length }} 件</span>
          </div>
          <div v-if="myGoodsList.length" class="deco-groups">
            <div v-for="group in decoGroups" :key="group.key" class="deco-group">
              <div class="deco-group-label">
                {{ group.label }}
                <span class="deco-group-count">{{ group.list.length }}</span>
              </div>
              <div v-if="group.list.length" class="deco-grid">
                <div
                  v-for="mg in group.list"
                  :key="mg.goodsId"
                  class="deco-item"
                  :class="{ on: mg.equipped }"
                >
                  <span class="deco-icon" :style="{ background: decoSoftBg(mg.item.icon) }">
                    <AppIcon :name="mg.item.icon || 'award'" :size="20" :color="decoColor(mg.item.icon)" />
                  </span>
                  <span class="deco-name">{{ mg.item.name }}</span>
                  <el-switch
                    v-model="mg.equipped"
                    size="small"
                    @change="(val) => toggleEquip(mg, val)"
                  />
                </div>
              </div>
              <div v-else class="deco-group-empty">
                暂未获得，去<span class="deco-link" @click="go('/shop')">金币商城</span>兑换
              </div>
            </div>
          </div>
          <div v-else class="deco-empty">
            还没有获得装扮，去<span class="deco-link" @click="go('/shop')">金币商城</span>兑换勋章和称号吧
          </div>
        </div>

        <!-- 账号安全 -->
        <div class="ws-card section-card">
          <div class="section-head">
            <AppIcon name="shield" :size="18" />
            <h3>账号安全</h3>
          </div>
          <div class="security-list">
            <div class="security-item">
              <div class="security-body">
                <div class="security-title">
                  QQ 邮箱
                  <el-tag v-if="userStore.userInfo?.email" size="small" type="success" effect="plain">已绑定</el-tag>
                  <el-tag v-else size="small" type="info" effect="plain">未绑定</el-tag>
                </div>
                <div class="security-desc">
                  {{ userStore.userInfo?.email || '绑定邮箱可用于找回密码和接收学习提醒' }}
                </div>
              </div>
              <el-button size="small" plain @click="openEmail">
                {{ userStore.userInfo?.email ? '换绑' : '绑定' }}
              </el-button>
            </div>

            <div class="security-item">
              <div class="security-body">
                <div class="security-title">登录密码</div>
                <div class="security-desc">建议定期修改密码以保护账号安全</div>
              </div>
              <el-button size="small" plain @click="openPassword">修改</el-button>
            </div>
          </div>
        </div>

        <!-- 快捷入口 -->
        <div class="ws-card section-card">
          <div class="section-head">
            <AppIcon name="layout-grid" :size="18" />
            <h3>快捷入口</h3>
          </div>
          <div class="quick-list">
            <div v-for="q in quickLinks" :key="q.path" class="ql-item" @click="go(q.path)">
              <AppIcon :name="q.icon" :size="16" class="ql-icon" />
              <span class="ql-name">{{ q.name }}</span>
              <span class="ql-desc">{{ q.desc }}</span>
              <AppIcon name="chevron-right" :size="14" class="ql-arrow" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 等级详情 -->
    <el-dialog v-model="levelVisible" title="我的等级" width="560px" append-to-body class="level-dialog">
      <div v-if="lvInfo" class="lv-wrap">
        <!-- 等级横幅 -->
        <div class="lv-banner">
          <div class="lv-medal">
            <span class="lv-medal-label">Lv</span>
            <span class="lv-medal-num">{{ lvInfo.level }}</span>
          </div>
          <div class="lv-banner-meta">
            <div class="lv-title">{{ lvInfo.titleIcon }} {{ lvInfo.title }}</div>
            <div class="lv-sub">
              成长值 <b>{{ lvInfo.exp }}</b>
              <template v-if="!lvInfo.isMax"> · 已超越多数同路人</template>
              <template v-else> · 词林之巅</template>
            </div>
          </div>
        </div>

        <!-- 升级进度 -->
        <div class="lv-progress">
          <span class="lv-progress-label">Lv.{{ lvInfo.level }}</span>
          <div class="lv-progress-track">
            <div class="lv-progress-fill" :style="{ width: `${lvInfo.percent}%` }"></div>
          </div>
          <span class="lv-progress-label">Lv.{{ lvInfo.isMax ? lvInfo.level : lvInfo.level + 1 }}</span>
        </div>
        <p class="lv-progress-tip">
          {{
            lvInfo.isMax
              ? '你已抵达 20 级满级，成长值会继续累积，见证你的每一份坚持'
              : `再获得 ${lvInfo.remainExp} 成长值即可升到 Lv.${lvInfo.level + 1}，领取升级奖励`
          }}
        </p>

        <!-- 升级奖励 -->
        <div class="lv-section">
          <div class="lv-section-head">
            <h4>升级奖励</h4>
            <span v-if="lvInfo.hasClaimable" class="lv-claim-hint">
              {{ lvInfo.claimableLevels.length }} 个待领取
            </span>
          </div>
          <div class="lv-reward-track">
            <div
              v-for="r in lvInfo.rewardList"
              :key="r.level"
              class="lv-reward-node"
              :class="[r.status, { claiming: claimingLevel === r.level }]"
              @click="r.status === 'claimable' && claimLevel(r.level)"
            >
              <div class="lv-reward-badge">
                <AppIcon v-if="r.status === 'claimed'" name="check" :size="13" />
                <AppIcon v-else-if="r.status === 'locked'" name="lock" :size="11" />
                <template v-else>{{ r.level }}</template>
              </div>
              <div class="lv-reward-gain">
                <span class="lv-coin">🪙{{ r.coin }}</span>
                <span v-if="r.ai" class="lv-ai">+{{ r.ai }}AI</span>
              </div>
            </div>
          </div>
          <p class="lv-reward-tip">每升 1 级可领一次金币；Lv.5 / 10 / 15 / 20 里程碑额外赠送永久 AI 额度</p>
        </div>

        <!-- 成长值获取途径 -->
        <div class="lv-section">
          <div class="lv-section-head">
            <h4>如何获得成长值</h4>
          </div>
          <div class="lv-rule-grid">
            <div v-for="rule in lvInfo.expRules" :key="rule.name" class="lv-rule-item">
              <div class="lv-rule-top">
                <AppIcon :name="rule.icon" :size="15" />
                <span class="lv-rule-name">{{ rule.name }}</span>
              </div>
              <div class="lv-rule-desc">{{ rule.desc }}</div>
              <div class="lv-rule-exp">+{{ rule.exp }}</div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="lv-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        正在加载等级信息…
      </div>
    </el-dialog>

    <!-- 编辑资料 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="420px">
      <div class="edit-avatar-row">
        <div class="edit-avatar-wrap" @click="triggerAvatar">
          <el-avatar :size="72" :src="profileForm.avatar || ''" class="edit-avatar">
            {{ avatarText }}
          </el-avatar>
          <div class="edit-avatar-mask">
            <AppIcon name="camera" :size="18" color="#fff" />
          </div>
        </div>
        <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarChange" />
        <div class="edit-avatar-actions">
          <el-button size="small" plain :loading="avatarUploading" @click="triggerAvatar">
            {{ profileForm.avatar ? '更换头像' : '上传头像' }}
          </el-button>
          <el-button
            v-if="profileForm.avatar"
            class="reset-avatar-btn"
            link
            :disabled="avatarUploading"
            @click="profileForm.avatar = ''"
          >
            恢复默认头像
          </el-button>
        </div>
      </div>
      <el-form label-position="top" class="edit-form">
        <el-form-item label="昵称">
          <el-input v-model="profileForm.nickname" placeholder="输入昵称" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="座右铭">
          <el-input
            v-model="profileForm.bio"
            type="textarea"
            :rows="3"
            placeholder="写下激励自己的话"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 换绑邮箱 -->
    <el-dialog v-model="emailVisible" :title="userStore.userInfo?.email ? '换绑 QQ 邮箱' : '绑定 QQ 邮箱'" width="420px">
      <el-form label-position="top">
        <el-form-item v-if="userStore.userInfo?.email" label="当前已绑定邮箱">
          <el-input v-model="userStore.userInfo.email" disabled>
            <template #prefix>
              <el-icon><Message /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="emailAgree" class="verify-check">
            我已确认是本人操作，并同意接收验证码邮件
          </el-checkbox>
        </el-form-item>
        <el-form-item label="验证码">
          <div class="code-row">
            <el-input v-model="emailForm.code" placeholder="6 位验证码" maxlength="6">
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
            </el-input>
            <el-button
              :disabled="codeCountdown > 0 || emailCodeSending"
              :loading="emailCodeSending"
              @click="sendEmailCodeClick"
            >
              {{ codeCountdown > 0 ? `${codeCountdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
          <p class="form-tip">
            验证码将发送至
            <b>{{ userStore.userInfo?.email || emailForm.email || '你填写的新邮箱' }}</b>
          </p>
        </el-form-item>
        <el-form-item label="新邮箱">
          <el-input v-model="emailForm.email" placeholder="例如：123456@qq.com" maxlength="100">
            <template #prefix>
              <el-icon><Message /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emailVisible = false">取消</el-button>
        <el-button type="primary" :loading="emailSaving" @click="saveEmail">确认</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-alert
        v-if="!userStore.userInfo?.email"
        title="请先绑定邮箱后再修改密码"
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-form v-else label-position="top">
        <el-form-item label="已绑定邮箱">
          <el-input v-model="userStore.userInfo.email" disabled>
            <template #prefix>
              <el-icon><Message /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="pwdAgree" class="verify-check">
            我已确认是本人操作，并同意接收验证码邮件
          </el-checkbox>
        </el-form-item>
        <el-form-item label="邮箱验证码">
          <div class="code-row">
            <el-input v-model="pwdForm.code" placeholder="6 位验证码" maxlength="6">
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
            </el-input>
            <el-button
              :disabled="pwdCodeCountdown > 0 || pwdCodeSending"
              :loading="pwdCodeSending"
              @click="sendPwdCodeClick"
            >
              {{ pwdCodeCountdown > 0 ? `${pwdCodeCountdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
          <p class="form-tip">验证码将发送至 <b>{{ userStore.userInfo.email }}</b></p>
        </el-form-item>
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码">
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20 位字符">
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码">
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" :disabled="!userStore.userInfo?.email" @click="savePassword">
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <!-- 图形验证弹层 -->
    <CaptchaModal ref="captchaModalRef" title="安全验证" @verified="onCaptchaVerified" />

    <!-- 备考资料确认弹窗 -->
    <Teleport to="body">
      <Transition name="res-dlg">
        <div v-if="resDialog.visible" class="res-dlg-mask" @click.self="closeResDialog">
          <div class="res-dlg">
            <span class="res-dlg-icon" :style="{ background: resDialogMeta.color + '1A' }">
              <AppIcon :name="resDialogMeta.icon" :size="26" :color="resDialogMeta.color" />
            </span>
            <h4 class="res-dlg-title">
              {{ resDialog.resource?.owned ? '下载' : '解锁' }}「{{ resDialog.resource?.item.name }}」
            </h4>
            <p class="res-dlg-text">
              {{
                resDialog.resource?.owned
                  ? '已放入你的资料库，可随时重复下载，现在开始？'
                  : `该资料需 ${resDialog.resource?.item.price} 金币兑换，用学习赚来的金币带它回家吧`
              }}
            </p>
            <div class="res-dlg-btns">
              <button class="res-dlg-btn ghost" @click="closeResDialog">再想想</button>
              <button class="res-dlg-btn primary" @click="confirmResDialog">
                {{ resDialog.resource?.owned ? '立即下载' : '去兑换' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, Message, Key, Loading } from '@element-plus/icons-vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { decoColor, decoSoftBg } from '@/utils/deco-colors'
import CaptchaModal from '@/components/common/CaptchaModal.vue'
import { updateProfile, sendEmailCode, updateEmail, changePassword, uploadAvatar, saveDailyGoal, getLevelInfo, claimLevelReward, getAchievements } from '@/api/user'
import { listWordBook, dueReview } from '@/api/wordBook'
import { myGoods, equipGoods, shopItems, downloadResource } from '@/api/shop'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const WORDS_PER_LEVEL = 50
const DAILY_GOAL = 20

const editVisible = ref(false)
const saving = ref(false)
const avatarUploading = ref(false)
const avatarInput = ref(null)
const profileForm = reactive({ nickname: '', bio: '', avatar: '' })

const emailVisible = ref(false)
const emailSaving = ref(false)
const emailCodeSending = ref(false)
const emailAgree = ref(false)
const codeCountdown = ref(0)
const emailForm = reactive({ email: '', code: '' })

const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdCodeSending = ref(false)
const pwdAgree = ref(false)
const pwdCodeCountdown = ref(0)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '', code: '' })

// 图形验证弹层（email 换绑邮箱 / pwd 修改密码）
const captchaModalRef = ref()
const captchaAction = ref('')
const verifying = ref(false)

const wordBookCount = ref(0)
const dueCount = ref(0)

const avatarText = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || 'W'
  return name.charAt(0).toUpperCase()
})

const totalWords = computed(() => userStore.userInfo?.totalWords ?? 0)

/* ============ 等级体系 v2（后端成长值驱动，前端兜底旧公式） ============ */
const lvInfo = ref(null)
const levelVisible = ref(false)
const claimingLevel = ref(0)

/** 等级：优先后端等级体系，接口未返回时按累计词数兜底 */
const level = computed(() => lvInfo.value?.level ?? Math.floor(totalWords.value / WORDS_PER_LEVEL) + 1)
/** 当前等级内经验进度百分比（0-100），tooltip 用 */
const levelPercent = computed(() => {
  if (lvInfo.value) return lvInfo.value.percent
  return ((totalWords.value % WORDS_PER_LEVEL) / WORDS_PER_LEVEL) * 100
})

/** 拉取等级详情（失败静默，徽章回落旧公式） */
async function loadLevelInfo() {
  try {
    lvInfo.value = await getLevelInfo()
  } catch (e) {
    /* 静默忽略 */
  }
}

/** 打开等级详情弹窗（先刷新一次保证数据最新） */
async function openLevelDialog() {
  levelVisible.value = true
  await loadLevelInfo()
}

/** 领取等级升级奖励 */
async function claimLevel(level) {
  if (claimingLevel.value) return
  claimingLevel.value = level
  try {
    const data = await claimLevelReward(level)
    lvInfo.value = data
    const parts = []
    if (data.coinAward) parts.push(`+${data.coinAward} 金币`)
    if (data.aiAward) parts.push(`+${data.aiAward} 次词灵AI`)
    ElMessage.success(`Lv.${level} 升级奖励已到账：${parts.join(' ')}`)
    // 刷新顶部导航 AI 额度显示
    userStore.refreshQuota()
  } catch (e) {
    /* 错误提示由拦截器统一处理 */
  } finally {
    claimingLevel.value = 0
  }
}

/** 商店里佩戴的称号/勋章，未佩戴时不展示 */
const equipped = ref(null)
const heroTag = computed(() => equipped.value?.name || '')
const heroTagIcon = computed(() => equipped.value?.icon || '')

/** 我拥有的装扮（勋章/称号等） */
const myGoodsList = ref([])

/* 装扮分组：勋章 / 称号 两大体系（备考资料已在后端过滤，不属于装扮） */
const decoGroups = computed(() => {
  const medal = myGoodsList.value.filter((g) => g.item?.category === 'medal')
  const title = myGoodsList.value.filter((g) => g.item?.category === 'title')
  return [
    { key: 'medal', label: '勋章', list: medal },
    { key: 'title', label: '称号', list: title }
  ]
})

const joinDate = computed(() => {
  const raw = userStore.userInfo?.createdAt
  if (!raw) return '-'
  const d = new Date(raw)
  if (isNaN(d.getTime())) return raw.slice(0, 10)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

const todayGreet = computed(() => {
  const h = new Date().getHours()
  if (h < 5) return '凌晨好'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const wk = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${wk}`
})

// CET-4/6 倒计时：考试日通常为 6 月、12 月的第二个周六
function getNthSaturdayOfMonth(year, month, n) {
  const first = new Date(year, month - 1, 1)
  const offsetToSat = (6 - first.getDay() + 7) % 7
  const firstSatDay = 1 + offsetToSat
  return new Date(year, month - 1, firstSatDay + (n - 1) * 7)
}

function getNextCetDate() {
  const now = new Date()
  now.setHours(0, 0, 0, 0)
  const candidates = []
  for (let year = now.getFullYear() - 1; year <= now.getFullYear() + 1; year++) {
    candidates.push(getNthSaturdayOfMonth(year, 6, 2))
    candidates.push(getNthSaturdayOfMonth(year, 12, 2))
  }
  candidates.sort((a, b) => a - b)
  return candidates.find((d) => d.getTime() >= now.getTime()) || candidates[candidates.length - 1]
}

const cetLevel = ref(Number(localStorage.getItem('cetLevel')) || 4)

function setCetLevel(n) {
  cetLevel.value = n
  localStorage.setItem('cetLevel', String(n))
}

// CET 考试开考时间固定为上午 9:00
function pad2(n) {
  return String(n).padStart(2, '0')
}

const now = ref(Date.now())
let cetTimer = null
onMounted(() => {
  cetTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})
onBeforeUnmount(() => {
  if (cetTimer) clearInterval(cetTimer)
})

const cetInfo = computed(() => {
  const examDate = getNextCetDate()
  const examTime = new Date(examDate)
  // CET-4 上午 9:00 开考；CET-6 下午 15:00 开考
  examTime.setHours(cetLevel.value === 4 ? 9 : 15, 0, 0, 0)
  const diffMs = examTime.getTime() - now.value
  const passed = diffMs <= 0
  const total = Math.max(0, Math.floor(diffMs / 1000))
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const session = examDate.getMonth() === 5 ? '上半年' : '下半年'
  const wk = ['日', '一', '二', '三', '四', '五', '六'][examDate.getDay()]
  const dateText = `${examDate.getFullYear()} 年 ${examDate.getMonth() + 1} 月 ${examDate.getDate()} 日 · 星期${wk}`
  return { examDate, days, hours, minutes, seconds, session, dateText, passed }
})

const todayPercent = computed(() => {
  const v = userStore.userInfo?.todayWords ?? 0
  const goal = userStore.userInfo?.dailyGoal ?? DAILY_GOAL
  return Math.min(Math.round((v / goal) * 100), 100)
})

const dailyGoal = computed(() => userStore.userInfo?.dailyGoal ?? DAILY_GOAL)

// 每日目标编辑
const goalPopoverVisible = ref(false)
const goalDraft = ref(20)
const goalSaving = ref(false)
function openGoalPopover() {
  goalDraft.value = dailyGoal.value
  goalPopoverVisible.value = true
}
async function confirmDailyGoal() {
  const n = Number(goalDraft.value)
  if (!Number.isFinite(n) || n < 5 || n > 200) {
    ElMessage.warning('每日目标需在 5 - 200 之间')
    return
  }
  goalSaving.value = true
  try {
    const data = await saveDailyGoal(n)
    userStore.userInfo = { ...userStore.userInfo, ...data }
    goalPopoverVisible.value = false
    ElMessage.success(`已将每日目标设为 ${n} 词`)
  } catch (e) {
    /* 错误提示由拦截器统一处理 */
  } finally {
    goalSaving.value = false
  }
}

const statCards = computed(() => {
  const info = userStore.userInfo
  return [
    { label: '累计单词', value: info?.totalWords ?? 0, icon: 'book-open' },
    { label: '连续天数', value: info?.studyDays ?? 0, icon: 'flame' },
    { label: '生词本', value: wordBookCount.value, icon: 'library' },
    { label: '待复习', value: dueCount.value, icon: 'rotate-ccw' },
    { label: 'AI 额度', value: userStore.aiQuotaRemain, icon: 'sparkles' }
  ]
})

/* ============ 成就（后端懒解锁，达成时间永久保留） ============ */
const ach = ref(null)

/** 个人中心只展示前 4 项，完整列表在成就殿堂页 */
const visibleAchievements = computed(() => (ach.value?.list || []).slice(0, 4))

async function loadAchievements() {
  try {
    ach.value = await getAchievements()
  } catch (e) {
    /* 静默忽略，保持空态 */
  }
}

/** 成就达成时间格式化：2026-09-06 */
function formatAchDate(raw) {
  if (!raw) return ''
  const d = new Date(raw)
  if (isNaN(d.getTime())) return String(raw).slice(0, 10)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const quickLinks = [
  { icon: 'sparkles', name: '词灵AI', desc: 'AI 阅读解析与答疑', path: '/ai-assistant' },
  { icon: 'bookmark', name: '我的AI笔记', desc: '查看收藏的 AI 内容', path: '/ai-note' },
  { icon: 'book-open', name: '背单词复习', desc: '艾宾浩斯复习计划', path: '/review' },
  { icon: 'library', name: '生词本', desc: '管理我的生词', path: '/word-book' },
  { icon: 'gamepad-2', name: '趣味乐园', desc: '10 款单词小游戏', path: '/game-park' },
  { icon: 'shopping-bag', name: '金币商城', desc: '兑换虚拟装扮', path: '/shop' },
  { icon: 'chart-line', name: '学习报告', desc: '学习数据总览', path: '/report' },
  { icon: 'settings', name: '设置', desc: '账号与偏好设置', path: '/settings' }
]

onMounted(() => {
  loadStats()
  loadEquipped()
  loadLevelInfo()
  loadAchievements()
  loadResources()
})

/* ============ 备考资料 ============ */
const resTab = ref('all')
const resTabs = [
  { key: 'all', label: '全部' },
  { key: 'owned', label: '已拥有' },
  { key: 'unowned', label: '未拥有' }
]
const resources = ref([])

const filteredResources = computed(() => {
  if (resTab.value === 'owned') return resources.value.filter((r) => r.owned)
  if (resTab.value === 'unowned') return resources.value.filter((r) => !r.owned)
  return resources.value
})

async function loadResources() {
  try {
    const res = await shopItems()
    resources.value = (res || []).filter((g) => g.item?.category === 'resource')
  } catch {
    resources.value = []
  }
}

/* 每份资料的专属图标与颜色（resourceKey → 图标/封面色） */
const RESOURCE_META = {
  zhongkao_words: { icon: 'book-text', color: '#3FA372' },
  cet4_words: { icon: 'send', color: '#4A90D9' },
  cet6_words: { icon: 'book-open', color: '#3D9A7E' },
  cet6_translate: { icon: 'languages', color: '#4C6FD9' },
  phrases: { icon: 'bookmark', color: '#E8A13D' },
  writing: { icon: 'pen-line', color: '#E8735C' },
  gaokao_words: { icon: 'book-marked', color: '#D95845' },
  kaoyan_words: { icon: 'target', color: '#8C6D4E' }
}

function resMeta(r) {
  return RESOURCE_META[r.item?.resourceKey] || { icon: 'file-text', color: '#8AA39B' }
}

/* 3 条/页轻分页 */
const resPage = ref(1)
const RES_PAGE_SIZE = 3

const resTotalPages = computed(() =>
  Math.max(1, Math.ceil(filteredResources.value.length / RES_PAGE_SIZE))
)

const pagedResources = computed(() => {
  const start = (resPage.value - 1) * RES_PAGE_SIZE
  return filteredResources.value.slice(start, start + RES_PAGE_SIZE)
})

watch([resTab, filteredResources], () => {
  if (resPage.value > resTotalPages.value) resPage.value = 1
})

/* 资料确认弹窗（C 端精致样式） */
const resDialog = reactive({ visible: false, resource: null })

const resDialogMeta = computed(() =>
  resDialog.resource ? resMeta(resDialog.resource) : { icon: 'file-text', color: '#8AA39B' }
)

async function onResourceClick(r) {
  resDialog.resource = r
  resDialog.visible = true
}

function closeResDialog() {
  resDialog.visible = false
}

async function confirmResDialog() {
  const r = resDialog.resource
  if (!r) return
  if (!r.owned) {
    resDialog.visible = false
    router.push('/shop')
    return
  }
  resDialog.visible = false
  try {
    const blob = await downloadResource(r.item.id)
    if (blob && blob.type && blob.type.includes('application/json')) {
      let msg = '下载失败，请稍后再试'
      try {
        msg = JSON.parse(await blob.text()).message || msg
      } catch {
        /* 忽略 */
      }
      ElMessage.error(msg)
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${r.item.name}（词灵学园）.pdf`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('资料已就绪，请在浏览器下载列表查看')
  } catch {
    ElMessage.error('下载失败，请稍后再试')
  }
}

async function loadStats() {
  try {
    const [wbRes, dueRes] = await Promise.all([
      listWordBook({ page: 1, size: 1 }).catch(() => ({ total: 0 })),
      dueReview(100).catch(() => [])
    ])
    wordBookCount.value = Number(wbRes.total || 0)
    dueCount.value = Array.isArray(dueRes) ? dueRes.length : 0
  } catch (e) {
    /* 静默忽略 */
  }
}

/** 拉取我的装扮列表，并同步顶部称号展示：称号优先，其次勋章 */
async function loadEquipped() {
  try {
    const goods = await myGoods()
    myGoodsList.value = goods || []
  } catch (e) {
    /* 拉取失败回落等级称号 */
  }
  syncEquipped()
}

/** 由装扮列表推导顶部展示的称号/勋章 */
function syncEquipped() {
  const on = myGoodsList.value.filter((g) => g.equipped && g.item)
  const title = on.find((g) => g.item.category === 'title')
  const medal = on.find((g) => g.item.category === 'medal')
  const hit = title || medal
  equipped.value = hit ? { name: hit.item.name, icon: hit.item.icon } : null
}

/** 佩戴/卸下装扮（同类目互斥） */
async function toggleEquip(mg, val) {
  try {
    await equipGoods(mg.goodsId, val)
    if (val) {
      myGoodsList.value.forEach((g) => {
        if (g.goodsId !== mg.goodsId && g.item && g.item.category === mg.item.category) {
          g.equipped = false
        }
      })
    }
    ElMessage.success(val ? `已佩戴「${mg.item.name}」` : `已卸下「${mg.item.name}」`)
    syncEquipped()
  } catch (e) {
    mg.equipped = !val
  }
}

function go(path) {
  router.push(path)
}

/* 编辑资料 */
function openEditProfile() {
  profileForm.nickname = userStore.userInfo?.nickname || ''
  profileForm.bio = userStore.userInfo?.bio || ''
  profileForm.avatar = userStore.userInfo?.avatar || ''
  editVisible.value = true
}

function triggerAvatar() {
  avatarInput.value?.click()
}

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过 2MB')
    return
  }
  avatarUploading.value = true
  try {
    const url = await uploadAvatar(file)
    profileForm.avatar = url
    ElMessage.success('头像上传成功')
  } finally {
    avatarUploading.value = false
    e.target.value = ''
  }
}

async function saveProfile() {
  if (!profileForm.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    const data = await updateProfile({
      nickname: profileForm.nickname.trim(),
      bio: profileForm.bio.trim(),
      avatar: profileForm.avatar
    })
    userStore.userInfo = data
    ElMessage.success('资料已更新')
    editVisible.value = false
  } finally {
    saving.value = false
  }
}

/* 邮箱 */
function openEmail() {
  emailForm.email = ''
  emailForm.code = ''
  emailAgree.value = false
  emailVisible.value = true
}

function sendEmailCodeClick() {
  if (!emailAgree.value) {
    ElMessage.warning('请先勾选「确认本人操作」后再获取验证码')
    return
  }
  const hasOld = !!userStore.userInfo?.email
  const targetEmail = hasOld ? userStore.userInfo.email : emailForm.email.trim()
  if (!targetEmail || !/^\S+@\S+\.\S+$/.test(targetEmail)) {
    ElMessage.warning(hasOld ? '当前未绑定有效邮箱' : '请先输入新邮箱')
    return
  }
  captchaAction.value = 'email'
  captchaModalRef.value?.open()
}

/** 图形验证通过后按动作发送邮箱验证码 */
async function onCaptchaVerified({ captchaId }) {
  if (verifying.value) return
  verifying.value = true
  const action = captchaAction.value
  if (action === 'email') {
    const hasOld = !!userStore.userInfo?.email
    const targetEmail = hasOld ? userStore.userInfo.email : emailForm.email.trim()
    const type = hasOld ? 'change_email' : 'bind_email'
    emailCodeSending.value = true
    try {
      const remain = await sendEmailCode(type, targetEmail, captchaId)
      ElNotification({
        title: '验证码已发送',
        message: `已发送至 ${targetEmail}，请查收邮件\n今日还可发送 ${remain} 次 · 同一邮箱 60 秒内只能发一次 · 10 分钟内有效`,
        type: 'success',
        duration: 5000,
        position: 'top-right'
      })
      captchaModalRef.value?.onVerifiedSuccess()
      startCountdown('email')
    } catch (e) {
      captchaModalRef.value?.close()
    } finally {
      emailCodeSending.value = false
    }
  } else if (action === 'pwd') {
    pwdCodeSending.value = true
    try {
      const remain = await sendEmailCode('change_password', userStore.userInfo.email, captchaId)
      ElNotification({
        title: '验证码已发送',
        message: `已发送至 ${userStore.userInfo.email}，请查收邮件\n今日还可发送 ${remain} 次 · 同一邮箱 60 秒内只能发一次 · 10 分钟内有效`,
        type: 'success',
        duration: 5000,
        position: 'top-right'
      })
      captchaModalRef.value?.onVerifiedSuccess()
      startCountdown('pwd')
    } catch (e) {
      captchaModalRef.value?.close()
    } finally {
      pwdCodeSending.value = false
    }
  }
  verifying.value = false
}

async function saveEmail() {
  const email = emailForm.email.trim()
  if (!email || !/^\S+@\S+\.\S+$/.test(email)) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  if (!emailForm.code.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  emailSaving.value = true
  try {
    const data = await updateEmail(email, emailForm.code.trim())
    userStore.userInfo = data
    ElMessage.success(userStore.userInfo?.email ? '邮箱已换绑' : '邮箱已绑定')
    emailVisible.value = false
  } finally {
    emailSaving.value = false
  }
}

/* 密码 */
function openPassword() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdForm.code = ''
  pwdAgree.value = false
  pwdVisible.value = true
}

function sendPwdCodeClick() {
  if (!pwdAgree.value) {
    ElMessage.warning('请先勾选「确认本人操作」后再获取验证码')
    return
  }
  const email = userStore.userInfo?.email
  if (!email) {
    ElMessage.warning('请先绑定邮箱')
    return
  }
  captchaAction.value = 'pwd'
  captchaModalRef.value?.open()
}

async function savePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword || !pwdForm.code) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (pwdForm.newPassword.length < 6 || pwdForm.newPassword.length > 20) {
    ElMessage.warning('新密码长度需在 6-20 位之间')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdSaving.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword, pwdForm.code)
    ElMessage.success('密码已修改，请重新登录')
    pwdVisible.value = false
    await userStore.logout()
    router.push('/login')
  } finally {
    pwdSaving.value = false
  }
}

/* 验证码倒计时 */
let emailTimer = null
let pwdTimer = null
function startCountdown(type) {
  if (type === 'email') {
    codeCountdown.value = 60
    emailTimer && clearInterval(emailTimer)
    emailTimer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0) clearInterval(emailTimer)
    }, 1000)
  } else {
    pwdCodeCountdown.value = 60
    pwdTimer && clearInterval(pwdTimer)
    pwdTimer = setInterval(() => {
      pwdCodeCountdown.value--
      if (pwdCodeCountdown.value <= 0) clearInterval(pwdTimer)
    }, 1000)
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  padding-top: $sp-6;
}

/* ============ 身份卡：Editorial 风 ============ */
.profile-hero {
  overflow: hidden;
  padding: 0;
}

.hero-cover {
  height: 4px;
  background: $color-primary;
}

.hero-body {
  padding: $sp-5 $sp-6 $sp-6;
}

.hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $sp-4;
}

.hero-id {
  display: flex;
  align-items: center;
  gap: $sp-4;
}

.hero-avatar-wrap {
  position: relative;
  flex-shrink: 0;
  cursor: pointer;
}

.hero-avatar {
  background: $color-primary;
  color: #fff;
  font-size: 28px;
  font-weight: 600;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.hero-level-badge {
  position: absolute;
  right: -6px;
  bottom: -6px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #3a8c89 0%, #5ca9a5 100%);
  border-radius: $radius-pill;
  border: 2px solid $bg-card;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(58, 140, 137, 0.35);
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover {
    transform: translateY(-1px) scale(1.06);
    box-shadow: 0 4px 10px rgba(58, 140, 137, 0.45);
  }

  &.has-reward {
    background: linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%);
    box-shadow: 0 2px 8px rgba(250, 140, 22, 0.4);
  }
}

/** 徽章红点：有待领升级奖励时提示 */
.lv-badge-dot {
  position: absolute;
  top: -4px;
  right: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f53f3f;
  border: 1.5px solid #fff;
  animation: lv-dot-breathe 1.8s ease-in-out infinite;
}

@keyframes lv-dot-breathe {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.25);
    opacity: 0.75;
  }
}

@media (prefers-reduced-motion: reduce) {
  .lv-badge-dot {
    animation: none;
  }
}

/* ============ 等级详情弹窗 ============ */
.lv-wrap {
  padding: 0 4px;
}

.lv-banner {
  display: flex;
  align-items: center;
  gap: $sp-4;
  padding: $sp-5 $sp-6;
  border-radius: $radius-card;
  background:
    radial-gradient(circle at 85% 20%, rgba(255, 255, 255, 0.14) 0, transparent 42%),
    $gradient-primary;
  color: #fff;
}

.lv-medal {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  border: 1.5px solid rgba(255, 255, 255, 0.45);
  box-shadow: inset 0 0 12px rgba(255, 255, 255, 0.12);

  .lv-medal-label {
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.08em;
    opacity: 0.85;
    line-height: 1;
  }

  .lv-medal-num {
    font-size: 24px;
    font-weight: 700;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
  }
}

.lv-banner-meta {
  min-width: 0;
}

.lv-title {
  font-size: $fs-xl;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.lv-sub {
  margin-top: 4px;
  font-size: $fs-sm;
  opacity: 0.88;

  b {
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }
}

.lv-progress {
  display: flex;
  align-items: center;
  gap: $sp-3;
  margin-top: $sp-5;
}

.lv-progress-label {
  flex-shrink: 0;
  font-size: $fs-sm;
  font-weight: 600;
  color: $text-title;
  font-variant-numeric: tabular-nums;
}

.lv-progress-track {
  flex: 1;
  height: 8px;
  border-radius: $radius-pill;
  background: $gray-3;
  overflow: hidden;
}

.lv-progress-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: $gradient-primary;
  transition: width $transition-slow;
}

.lv-progress-tip {
  margin-top: $sp-2;
  font-size: $fs-sm;
  color: $text-caption;
}

.lv-section {
  margin-top: $sp-5;
}

.lv-section-head {
  display: flex;
  align-items: center;
  gap: $sp-2;

  h4 {
    margin: 0;
    font-size: $fs-md;
    font-weight: 600;
    color: $text-title;
  }
}

.lv-claim-hint {
  padding: 1px 8px;
  font-size: $fs-xs;
  font-weight: 500;
  color: $color-warning;
  background: $color-warning-soft;
  border-radius: $radius-pill;
}

/* 奖励节点横向轨道 */
.lv-reward-track {
  display: flex;
  gap: $sp-2;
  margin-top: $sp-3;
  padding: $sp-2 2px $sp-3;
  overflow-x: auto;
  scrollbar-width: thin;

  &::-webkit-scrollbar {
    height: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: $gray-4;
    border-radius: $radius-pill;
  }
}

.lv-reward-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  width: 52px;
  cursor: default;

  .lv-reward-badge {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    font-size: $fs-sm;
    font-weight: 700;
    color: $text-caption;
    background: $gray-2;
    border: 1.5px dashed $gray-4;
    font-variant-numeric: tabular-nums;
  }

  .lv-reward-gain {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1px;

    .lv-coin {
      font-size: $fs-xs;
      color: $text-caption;
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }

    .lv-ai {
      font-size: 10px;
      color: $color-primary;
      white-space: nowrap;
      font-weight: 600;
    }
  }

  /* 可领取：暖橙描边 + 呼吸光晕 */
  &.claimable {
    cursor: pointer;

    .lv-reward-badge {
      color: #fff;
      background: linear-gradient(135deg, #fa8c16, #ffc53d);
      border: 1.5px solid transparent;
      box-shadow: 0 0 0 0 rgba(250, 140, 22, 0.35);
      animation: lv-node-breathe 2s ease-in-out infinite;
    }

    .lv-coin {
      color: $color-warning;
      font-weight: 600;
    }

    &:hover .lv-reward-badge {
      transform: scale(1.08);
      box-shadow: 0 3px 10px rgba(250, 140, 22, 0.4);
      animation: none;
    }
  }

  /* 已领取：浅绿 */
  &.claimed {
    .lv-reward-badge {
      color: $color-success;
      background: $color-success-soft;
      border: 1.5px solid rgba(82, 196, 26, 0.35);
    }

    .lv-coin {
      color: $text-disabled;
      text-decoration: line-through;
    }
  }

  &.claiming {
    opacity: 0.6;
    pointer-events: none;
  }
}

@keyframes lv-node-breathe {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(250, 140, 22, 0.3);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(250, 140, 22, 0.08);
  }
}

@media (prefers-reduced-motion: reduce) {
  .lv-reward-node.claimable .lv-reward-badge {
    animation: none;
  }
}

.lv-reward-tip {
  margin-top: $sp-1;
  font-size: $fs-xs;
  color: $text-disabled;
}

/* 成长值获取途径 */
.lv-rule-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $sp-2;
  margin-top: $sp-3;
}

.lv-rule-item {
  padding: $sp-3 $sp-2;
  border-radius: $radius-base;
  background: $bg-soft;
  border: 1px solid $border-light;
  text-align: center;
  transition: border-color $transition-fast, background $transition-fast;

  &:hover {
    background: $primary-1;
    border-color: $primary-3;
  }

  .lv-rule-top {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    color: $color-primary;
  }

  .lv-rule-name {
    font-size: $fs-sm;
    font-weight: 600;
    color: $text-title;
  }

  .lv-rule-desc {
    margin-top: 3px;
    font-size: $fs-xs;
    color: $text-caption;
  }

  .lv-rule-exp {
    margin-top: 5px;
    font-size: $fs-sm;
    font-weight: 700;
    color: $color-primary;
    font-variant-numeric: tabular-nums;
  }
}

.lv-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  padding: $sp-10 0;
  font-size: $fs-sm;
  color: $text-caption;
}

.hero-meta {
  min-width: 0;
}

.hero-greet {
  font-size: $fs-lg;
  font-weight: 600;
  color: $text-title;
  letter-spacing: -0.01em;
}

.hero-name-row {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin-top: 4px;
  flex-wrap: wrap;
}

.hero-username {
  font-size: $fs-sm;
  color: $text-caption;
  font-weight: 500;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 500;
  color: $color-primary;
  background: $primary-2;
  border-radius: $radius-pill;

  .hero-tag-icon {
    font-size: 12px;
    line-height: 1;
  }
}

.hero-joined {
  margin-top: 6px;
  font-size: $fs-sm;
  color: $text-disabled;
}

.hero-edit {
  flex-shrink: 0;
}

.hero-quote {
  margin-top: $sp-5;
  padding-left: $sp-4;
  border-left: 2px solid $color-primary;
  font-size: $fs-md;
  line-height: 1.6;
  color: $text-body;
  font-style: italic;

  &.placeholder {
    color: $text-caption;
    font-style: normal;
  }
}

/* ============ 数据叙事：双栏大数字 ============ */
.metric-row {
  margin-top: $sp-4;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $sp-4;
}

.metric-card {
  padding: $sp-5 $sp-6;
  display: flex;
  flex-direction: column;
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.metric-label {
  font-size: $fs-sm;
  color: $text-caption;
  letter-spacing: 0.02em;
}

.metric-num {
  margin-top: $sp-3;
  font-size: 44px;
  font-weight: 700;
  color: $text-title;
  line-height: 1.05;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;

  .metric-unit {
    margin-left: 4px;
    font-size: $fs-md;
    font-weight: 500;
    color: $text-caption;
  }
}

.metric-foot {
  margin-top: $sp-3;
  font-size: $fs-sm;
  color: $text-caption;
  font-variant-numeric: tabular-nums;

  b {
    font-weight: 600;
    color: $text-body;
  }
}

.metric-bar {
  margin-top: $sp-4;
  height: 3px;
  background: $bg-page;
  border-radius: $radius-pill;
  overflow: hidden;
}

.metric-bar-fill {
  height: 100%;
  background: $color-primary;
  border-radius: $radius-pill;
  transition: width $transition-normal;
}

/* CET 倒计时卡 */
.cet-tabs {
  display: inline-flex;
  gap: 2px;
  background: $bg-page;
  border-radius: $radius-base;
  padding: 2px;
}

.cet-tab {
  padding: 2px 8px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: $text-caption;
  border-radius: $radius-sm;
  cursor: pointer;
  transition: all $transition-fast;
  user-select: none;

  &:hover {
    color: $text-body;
  }

  &.active {
    background: $bg-card;
    color: $text-title;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  }
}

.cet-clock {
  margin-top: auto;
  padding-top: $sp-4;
  font-size: $fs-sm;
  font-weight: 500;
  color: $text-caption;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

/* ============ 数据概览：极简细线网格 ============ */
.stats-grid {
  margin-top: $sp-4;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  overflow: hidden;
}

.stat-cell {
  padding: $sp-4 $sp-5;
  border-right: 1px solid $border-light;
  transition: background $transition-fast;

  &:last-child {
    border-right: none;
  }

  &:hover {
    background: $bg-page;
  }
}

.stat-num {
  font-size: 26px;
  font-weight: 700;
  color: $text-title;
  line-height: 1.1;
  letter-spacing: -0.01em;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  margin-top: 4px;
  font-size: $fs-sm;
  color: $text-caption;
}

/* ============ 中部两栏 ============ */
.profile-main {
  margin-top: $sp-4;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $sp-4;
  align-items: start;
}

.profile-col {
  display: flex;
  flex-direction: column;
  gap: $sp-4;
}

.section-card {
  padding: $sp-5;
}

.section-head {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin-bottom: $sp-4;
  color: $text-title;

  h3 {
    font-size: $fs-lg;
    font-weight: 600;
    flex: 1;
    letter-spacing: 0.01em;
  }
}

.section-count {
  font-size: $fs-sm;
  color: $text-caption;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

/* 今日目标 */
.goal-info {
  flex: 1;
}

.goal-title {
  font-size: $fs-md;
  font-weight: 500;
  color: $text-body;
  display: flex;
  align-items: baseline;
  gap: 4px;

  b {
    font-size: $fs-lg;
    font-weight: 700;
    color: $text-title;
    font-variant-numeric: tabular-nums;
  }

  .goal-sep {
    color: $text-disabled;
    margin: 0 2px;
  }
}

.goal-num {
  font-weight: 700;
  color: $color-primary;
  padding: 0 2px;
}

.goal-num.editable {
  cursor: pointer;
  border-bottom: 1px dashed $color-primary;
  transition: color $transition-fast;

  &:hover {
    color: $color-primary-deep;
  }
}

.goal-bar {
  margin-top: $sp-3;
  height: 4px;
  border-radius: $radius-pill;
  background: $bg-page;
  overflow: hidden;
}

.goal-bar-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: $color-primary;
  transition: width $transition-normal;
}

.goal-tip {
  margin-top: 10px;
  font-size: $fs-sm;
  color: $text-caption;
  line-height: 1.5;
  display: flex;
  align-items: center;
  gap: $sp-3;
}

.goal-link {
  color: $color-primary;
  font-weight: 500;
  cursor: pointer;
  transition: opacity $transition-fast;

  &:hover {
    opacity: 0.75;
  }
}

.goal-popover {
  padding: 4px 2px;
}

.goal-popover-title {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-title;
  margin-bottom: 10px;
}

.goal-presets {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.goal-preset {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  font-size: $fs-sm;
  color: $text-regular;
  background: $gray-3;
  border-radius: $radius-base;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: $primary-2;
    color: $color-primary;
  }

  &.active {
    background: $color-primary;
    color: #fff;
  }
}

.goal-input {
  width: 100%;
  margin-bottom: 12px;
}

.goal-popover-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 成就：横向列表，左 icon 右文 */
.badge-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $sp-2;
}

.badge-item {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: $sp-3 $sp-4;
  border-radius: $radius-card;
  background: $bg-page;
  border: 1px solid $border-light;
  transition: all $transition-fast;
  opacity: 0.5;

  &.unlocked {
    opacity: 1;
    background: $bg-card;
    border-color: $border-light;
  }

  &:hover {
    border-color: $color-primary;
  }
}

.badge-icon {
  width: 38px;
  height: 38px;
  border-radius: $radius-base;
  background: $bg-page;
  color: $text-disabled;
  @include flex-center;
  flex-shrink: 0;
  transition: all $transition-fast;

  .unlocked & {
    background: $primary-2;
    color: $color-primary;
  }
}

.badge-text {
  min-width: 0;
}

.badge-name {
  font-size: $fs-base;
  font-weight: 600;
  color: $text-title;
}

.badge-desc {
  margin-top: 2px;
  font-size: 12px;
  color: $text-caption;
}

/* 成就点数（右上角汇总） */
.ach-points {
  margin-left: auto;
  font-size: $fs-md;
  font-weight: 700;
  color: $color-primary;
  font-variant-numeric: tabular-nums;

  i {
    font-style: normal;
    font-size: $fs-xs;
    font-weight: 400;
    color: $text-disabled;
  }
}

/* 单个成就的分值（行尾） */
.badge-points {
  margin-left: auto;
  flex-shrink: 0;
  padding: 1px 8px;
  font-size: $fs-xs;
  font-weight: 600;
  color: $text-caption;
  background: $gray-3;
  border-radius: $radius-pill;
  font-variant-numeric: tabular-nums;

  .unlocked & {
    color: $color-primary;
    background: $primary-2;
  }
}

/* 查看全部成就：主流纯文字链接，无边框无底色，悬浮主色高亮 */
.badge-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 100%;
  margin-top: $sp-2;
  padding: 8px 0 2px;
  font-size: $fs-sm;
  color: $text-caption;
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.2s ease;

  &:hover {
    color: $color-primary;
  }
}

/* 备考资料 */
.res-tabs {
  display: flex;
  gap: 18px;
}

.res-tab {
  padding: 4px 2px;
  font-size: 13px;
  color: $text-caption;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.2s ease;
}

.res-tab:hover {
  color: $color-primary;
}

.res-tab.active {
  color: $color-primary;
  font-weight: 600;
  border-bottom-color: $color-primary;
}

.res-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.res-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.res-item:hover {
  background: $bg-soft;

  .res-name {
    color: $color-primary;
  }
}

.res-name {
  flex: 1;
  font-size: $fs-sm;
  color: $text-primary;
  transition: color 0.2s ease;
}

.res-owned {
  font-size: 12px;
  color: $color-primary;
}

.res-price {
  font-size: 12px;
  color: #b08a3e;
}

.res-empty {
  padding: 16px 0;
  text-align: center;
  font-size: 13px;
  color: $text-caption;
}

.res-item-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.res-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: $sp-2;
  padding-top: 4px;
}

.res-page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: none;
  border: 1px solid $border-light;
  color: $text-secondary;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    color: $color-primary;
    border-color: $color-primary;
    background: $color-primary-soft;
  }

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }
}

.res-page-ind {
  font-size: 12px;
  color: $text-caption;
}

/* 备考资料确认弹窗（国内主流 C 端样式） */
.res-dlg-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 32px;
  background: rgba(15, 24, 22, 0.5);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.res-dlg {
  width: 300px;
  padding: 30px 24px 20px;
  text-align: center;
  background: #fff;
  border-radius: 20px;
  box-shadow: $shadow-lg;
}

.res-dlg-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  margin: 0 auto 14px;
  border-radius: 50%;
}

.res-dlg-title {
  margin-bottom: 8px;
  font-size: 17px;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.4;

  /* 资料名过长时换行不溢出 */
  word-break: break-all;
}

.res-dlg-text {
  margin-bottom: 22px;
  font-size: 13px;
  color: $text-caption;
  line-height: 1.65;
}

.res-dlg-btns {
  display: flex;
  gap: 10px;
}

.res-dlg-btn {
  flex: 1;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: $radius-pill;
  cursor: pointer;
  transition: all $transition-fast;

  &.ghost {
    background: $bg-soft;
    color: $text-secondary;

    &:hover {
      background: #eceff0;
      color: $text-primary;
    }

    &:active {
      transform: scale(0.97);
    }
  }

  &.primary {
    background: $gradient-primary;
    color: #fff;
    box-shadow: $shadow-primary;

    &:hover {
      opacity: 0.92;
      box-shadow: 0 6px 18px rgba(58, 140, 137, 0.28);
    }

    &:active {
      transform: scale(0.97);
      box-shadow: 0 2px 8px rgba(58, 140, 137, 0.2);
    }
  }
}

/* 弹窗过渡动画 */
.res-dlg-enter-active {
  transition: opacity 0.24s ease;

  .res-dlg {
    transition: transform 0.24s cubic-bezier(0.34, 1.4, 0.64, 1);
  }
}

.res-dlg-leave-active {
  transition: opacity 0.18s ease;

  .res-dlg {
    transition: transform 0.18s ease;
  }
}

.res-dlg-enter-from,
.res-dlg-leave-to {
  opacity: 0;

  .res-dlg {
    transform: scale(0.88);
  }
}

/* 我的装扮 */
.deco-groups {
  display: flex;
  flex-direction: column;
  gap: $sp-4;
}

.deco-group-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: $sp-2;
  font-size: $fs-sm;
  font-weight: 600;
  color: $text-secondary;

  .deco-group-count {
    padding: 0 7px;
    border-radius: $radius-pill;
    background: #f0f4f2;
    color: $text-caption;
    font-size: $fs-xs;
    font-weight: 500;
    line-height: 18px;
  }
}

.deco-group-empty {
  padding: 10px $sp-3;
  border: 1px dashed $border-color;
  border-radius: $radius-sm;
  font-size: $fs-xs;
  color: $text-caption;

  .deco-link {
    color: $color-primary;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }
}

.deco-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $sp-2;
}

.deco-item {
  display: flex;
  align-items: center;
  gap: $sp-2;
  padding: 9px $sp-3;
  border-radius: $radius-base;
  background: $bg-page;
  border: 1px solid $border-light;
  transition: all $transition-fast;

  &:hover {
    border-color: $primary-3;
  }

  &.on {
    background: $primary-2;
    border-color: $primary-3;
  }
}

.deco-icon {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.deco-name {
  flex: 1;
  min-width: 0;
  font-size: $fs-sm;
  font-weight: 500;
  color: $text-title;
  @include ellipsis;
}

.deco-empty {
  padding: $sp-4;
  font-size: $fs-sm;
  color: $text-caption;
  text-align: center;
  line-height: 1.6;
  background: $bg-page;
  border-radius: $radius-base;
}

.deco-link {
  color: $color-primary;
  font-weight: 500;
  cursor: pointer;
  transition: opacity $transition-fast;

  &:hover {
    opacity: 0.75;
  }
}

/* 账号安全 */
.security-list {
  display: flex;
  flex-direction: column;
}

.security-item {
  display: flex;
  align-items: center;
  gap: $sp-3;
  padding: $sp-4 0;
  border-bottom: 1px solid $border-light;

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  &:first-child {
    padding-top: 0;
  }
}

.security-body {
  flex: 1;
  min-width: 0;
}

.security-title {
  display: flex;
  align-items: center;
  gap: $sp-2;
  font-size: $fs-base;
  font-weight: 600;
  color: $text-title;
}

.security-desc {
  margin-top: 2px;
  font-size: $fs-sm;
  color: $text-caption;
  @include ellipsis;
}

/* 快捷入口 */
.quick-list {
  display: flex;
  flex-direction: column;
}

.ql-item {
  display: flex;
  align-items: center;
  gap: $sp-2;
  padding: 12px 0;
  border-bottom: 1px solid $border-light;
  cursor: pointer;
  transition: color $transition-fast;
  color: $text-body;

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  &:first-child {
    padding-top: 0;
  }

  &:hover {
    .ql-name {
      color: $color-primary;
    }
    .ql-arrow {
      color: $color-primary;
      transform: translateX(2px);
    }
  }
}

.ql-icon {
  color: $text-caption;
  flex-shrink: 0;
}

.ql-name {
  font-size: $fs-base;
  font-weight: 500;
  color: $text-title;
  transition: color $transition-fast;
  flex-shrink: 0;
}

.ql-desc {
  flex: 1;
  font-size: $fs-sm;
  color: $text-caption;
  @include ellipsis;
  min-width: 0;
  text-align: right;
  margin-right: $sp-2;
}

.ql-arrow {
  color: $text-disabled;
  flex-shrink: 0;
  transition: all $transition-fast;
}

/* 编辑资料弹窗 */
.edit-avatar-row {
  display: flex;
  align-items: center;
  gap: $sp-4;
  margin-bottom: $sp-4;
}

.edit-avatar-wrap {
  position: relative;
  cursor: pointer;

  &:hover .edit-avatar-mask {
    opacity: 1;
  }
}

.edit-avatar {
  background: $color-primary;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
}

.edit-avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity $transition-fast;
}

.edit-avatar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.reset-avatar-btn {
  font-size: 12px;
  color: $text-secondary;

  &:hover {
    color: $color-primary;
    background: none;
  }
}

.edit-form {
  :deep(.el-form-item__label) {
    font-weight: 600;
  }
}

/* 验证码行 */
.code-row {
  display: flex;
  gap: $sp-2;

  .el-input {
    flex: 1;
  }

  .el-button {
    width: 120px;
  }
}

.form-tip {
  margin-top: 6px;
  font-size: $fs-sm;
  color: $text-caption;
  line-height: 1.4;

  &.warn {
    color: #d48806;
  }
}

.verify-check {
  margin-bottom: 2px;

  :deep(.el-checkbox__label) {
    font-size: $fs-sm;
    color: $text-regular;
  }
}

/* ============ 响应式 ============ */
@media (max-width: 1100px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .stat-cell:nth-child(3n) {
    border-right: none;
  }

  .stat-cell:nth-child(n+4) {
    border-top: 1px solid $border-light;
  }
}

@media (max-width: 900px) {
  .profile-main {
    grid-template-columns: 1fr;
  }

  .metric-row {
    grid-template-columns: 1fr;
  }

  .hero-top {
    flex-wrap: wrap;
  }
}

@media (max-width: 600px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-cell:nth-child(3n) {
    border-right: 1px solid $border-light;
  }

  .stat-cell:nth-child(2n) {
    border-right: none;
  }

  .stat-cell:nth-child(n+3) {
    border-top: 1px solid $border-light;
  }

  .code-row .el-button {
    width: 100px;
    padding: 8px 6px;
  }
}
</style>

<!-- 等级徽章 hover 提示：popper 挂载在 body 下，需非 scoped 样式 -->
<style lang="scss">
.el-popper.lv-tip-popper {
  padding: 0;
  border: none;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(29, 33, 41, 0.14);

  .lv-tip {
    width: 208px;
    padding: 12px 14px;
  }

  .lv-tip-head {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #1d2129;

    b {
      padding: 1px 8px;
      font-size: 12px;
      color: #fff;
      background: linear-gradient(135deg, #3a8c89, #5ca9a5);
      border-radius: 999px;
      font-variant-numeric: tabular-nums;
    }

    span {
      font-weight: 500;
    }
  }

  .lv-tip-bar {
    height: 6px;
    margin-top: 10px;
    border-radius: 999px;
    background: #f2f3f5;
    overflow: hidden;

    i {
      display: block;
      height: 100%;
      border-radius: 999px;
      background: linear-gradient(135deg, #3a8c89, #5ca9a5);
      transition: width 0.4s ease-out;
    }
  }

  .lv-tip-foot {
    margin-top: 7px;
    font-size: 12px;
    color: #86909c;
  }
}
</style>
