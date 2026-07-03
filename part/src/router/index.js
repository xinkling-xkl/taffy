import { createRouter, createWebHistory } from 'vue-router';
import SessionManager from '../utils/SessionManager.js';

const routes = [
    {
        path: '/',
        redirect: '/userlogin'
    },
    {
        path: '/userlogin',
        name: 'UserLogin',
        component: () => import('@/views/UserLogin.vue')
    },
    {
        path: '/admin',
        name: 'Admin',
        component: () => import('@/views/AdminInterFace.vue'),
        meta: {
            requiresAuth: true
        }
    },
    {
        path: '/parttime',
        name: 'Parttime',
        component: () => import('@/views/PartTime.vue'),
        meta: {
            requiresAuth: true
        }
    },
    {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: {
            requiresAuth: true
        }
    },
    {
        path: '/userregistration',
        name: 'UserRegistration',
        component: () => import('@/views/UserRegistration.vue')
    },
    {
        path: '/forgotpassword',
        name: 'ForgotPassword',
        component: () => import('@/views/ForgotPassword.vue')
    },
    {
        path: '/feedback',
        name: 'Feedback',
        component: () => import('@/views/Feedback.vue')
    },
    {
        path: '/job/:id',
        name: 'JobDetail',
        component: () => import('@/views/JobDetail.vue'),
        meta: {
            requiresAuth: true
        }
    },
    {
        path: '/find-students',
        name: 'FindStudents',
        component: () => import('@/views/FindStudents.vue'),
        meta: {
            requiresAuth: true
        }
    },
    {
        path: '/message/list',
        name: 'MessageList',
        component: () => import('@/views/MessageList.vue'),
        meta: {
            requiresAuth: true
        }
    },
    {
        path: '/message/detail/:id',
        name: 'MessageDetail',
        component: () => import('@/views/MessageDetail.vue'),
        meta: {
            requiresAuth: true
        }
    },
    {
        path: '/attendance',
        name: 'Attendance',
        component: () => import('@/views/Attendance.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/merchant-attendance',
        name: 'MerchantAttendance',
        component: () => import('@/views/MerchantAttendance.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/ai-match',
        name: 'AiMatch',
        component: () => import('@/views/AiMatch.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/my-accepted-jobs',
        name: 'MyAcceptedJobs',
        component: () => import('@/views/MyAcceptedJobs.vue'),
        meta: { requiresAuth: true }
    },
];

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
});


router.beforeEach((to, from, next) => {
    const isAuthenticated = SessionManager.isLoggedIn();
    if (to.meta.requiresAuth && !isAuthenticated) {
        next({ name: 'UserLogin' }); // 未登录则重定向到登录页
    } else {
        next(); // 必须调用 next()
    }
});

export default router;