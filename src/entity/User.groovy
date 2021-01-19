package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Type

import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob

@Entity
@DynamicUpdate
class User extends LongIdEntity  {
    /**
     * 用户登录名
     */
    @Column(unique = true)
    String name
    /**
     * 登录的密码
     */
    String password
    /**
     * 权限列表, 用豆号分割
     */
    @Lob
    @Basic
    @Type(type = "text")
    String permissions
    /**
     * 上次登录时间
     */
    Date login
}