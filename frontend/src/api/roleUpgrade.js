import request from '../utils/request'

export const submitRoleUpgradeApply = (data) => {
  return request({
    url: '/role-upgrade-applies',
    method: 'post',
    data
  })
}

export const getMyLatestRoleUpgradeApply = () => {
  return request({
    url: '/role-upgrade-applies/my/latest',
    method: 'get'
  })
}

export const adminGetRoleUpgradeApplies = (params) => {
  return request({
    url: '/admin/role-upgrade-applies',
    method: 'get',
    params
  })
}

export const adminUpdateRoleUpgradeApplyStatus = (id, params) => {
  return request({
    url: `/admin/role-upgrade-applies/${id}/status`,
    method: 'put',
    params
  })
}

