package com.github.jsoncat.core.ioc;

import com.github.jsoncat.annotation.ioc.Autowired;
import com.github.jsoncat.annotation.ioc.Qualifier;
import com.github.jsoncat.common.util.ReflectionUtil;
import com.github.jsoncat.core.aop.JdkAopProxyBeanPostProcessor;
import com.github.jsoncat.exception.CanNotDetermineTargetBeanException;
import com.github.jsoncat.exception.InterfaceNotHaveImplementedClassException;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author shuang.kou & tom
 * @createTime 2020年09月30日 07:51:00
 **/
public class DependencyInjection {

    //二级缓存
    private static final Map<String, Object> SINGLETON_OBJECTS = new ConcurrentHashMap<>(64);

    /**
     * 遍历ioc容器所有bean的属性, 为所有带@Autowired注解的属性注入实例
     */
    public static void dependencyInjection(String packageName) {
        Map<String, Object> beans = BeanFactory.BEANS;
        //创建好的bean都放入对象工厂
        if (beans.size() > 0) {
            BeanFactory.BEANS.values().forEach(bean -> {
                prepareBean(bean, packageName);
            });
        }
    }

    /**
     * 准备bean
     */
    private static void prepareBean(Object beanInstance, String packageName) {
        Class<?> beanClass = beanInstance.getClass();
        Field[] beanFields = beanClass.getDeclaredFields();
        //遍历bean的属性
        if (beanFields.length > 0) {
            for (Field beanField : beanFields) {
                if (beanField.isAnnotationPresent(Autowired.class)) {
                    //属性类型
                    Class<?> beanFieldClass = beanField.getType();
                    String beanName = beanFieldClass.getName();
                    boolean newSingleton = true;
                    Object beanFieldInstance = null;
                    if (SINGLETON_OBJECTS.containsKey(beanName)) {
                        beanFieldInstance = SINGLETON_OBJECTS.get(beanName);
                        newSingleton = false;
                    }
                    if (beanFieldInstance == null) {
                        if (beanFieldClass.isInterface()) {
                            @SuppressWarnings("unchecked")
                            Set<Class<?>> subClasses = ReflectionUtil.getSubClass(packageName, (Class<Object>) beanFieldClass);
                            if (subClasses.size() == 0) {
                                throw new InterfaceNotHaveImplementedClassException("interface does not have implemented class exception");
                            }
                            if (subClasses.size() == 1) {
                                Class<?> aClass = subClasses.iterator().next();
                                beanFieldInstance = ReflectionUtil.newInstance(aClass);
                            }
                            if (subClasses.size() > 1) {
                                Qualifier qualifier = beanField.getDeclaredAnnotation(Qualifier.class);
                                beanName = qualifier == null ? beanName : qualifier.value();
                                beanFieldInstance = BeanFactory.BEANS.get(beanName);
                            }

                        } else {
                            beanFieldInstance = BeanFactory.BEANS.get(beanName);
                        }
                        if (beanFieldInstance == null) {
                            throw new CanNotDetermineTargetBeanException("can not determine target bean");
                        } else {
                            SINGLETON_OBJECTS.put(beanName, beanFieldInstance);
                        }
                    }
                    if (newSingleton) {
                        prepareBean(beanFieldInstance, packageName);
                    }
                    //执行bean包装
                    BeanPostProcessor beanPostProcessor = new JdkAopProxyBeanPostProcessor(packageName);
                    beanFieldInstance = beanPostProcessor.postProcessAfterInitialization(beanFieldInstance, beanName);
                    ReflectionUtil.setField(beanInstance, beanField, beanFieldInstance);
                }
            }
        }
    }


}
