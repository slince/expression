## 目录

* [基本语法](#基本语法)
* [数组、`List`、`Map` 访问元素](#数组listmap-访问元素)
* [可选调用语法](#可选调用语法)
* [递归搜索后代属性](#递归搜索后代属性)
* [运算符](#运算符)
* [三元运算](#三元运算)
* [null 合并运算](#null-合并运算)

## 基本语法

基本的语法是对象的属性访问和方法调用：

```
user.profile.name
```
相当于 java 代码：

```java
user.getProfile().getName()
```

方法调用和 java 代码类似:

```
user.getProfile().getName()
```

调用行为约束和 java 一致，这意味着方法必须是 `public` 修饰的。 

## 数组、`List`、`Map` 访问元素

以Readme中对象结构为例，如果要获取小明的第一本书； `user.books[0]`; 中括号下标语法支持数组、`List`、`Map` 结构；

> 如果数组和 list 元素访问时越界，那么表达式语法也会越界；此行为和 java 默认行为保持一致；
> 不支持 collection 结构

## 可选调用语法

如果 java 代码 `user.getProfile()` 返回 `null` 导致空指针异常，那么表达式写法也同样会抛出异常。
为了避免此类问题安全地链式访问，引入了 javascript `es6` 的可选链式调用语法，上述表达式可以改写成如下形式：

```
user?.profile?.name
```

如果任一层返回了 `null`，那么调用就会停止并且将 `null` 返回给上一层；以案例中的对象结构为例，小明的第二本书是 `null`，

`user.books[1]?.name` 该语法可以安全的判断空对象 

## 递归搜索后代属性

```
`user..name`
```

该表达式会自递归查询所有后代名称为 `name` 的属性值；如果 `user` 为 `null`表达式执行时会抛出空指针异常；
同样的，子递归搜索也支持使用"可选调用语法"调整默认行为；

```
`user?..name`
```
如果`user`为空，则不会进行子递归查询

## 运算符

支持常见的一元运算符和二元运算符

二元运算符：

| 符号    | 案例               |  
|-------|------------------|
| `&&`  | `a && b`         |
| `^`   | `a ^ b`          |
| `&`   | `a & b`          |
| `==`  | `a == b`         |
| `!=`  | `a != b`         |
| `<`   | `a < b`          |
| `>`   | `a > b`          |
| `>=`  | `a >= b`         |
| `<=`  | `a <= b`         |
| `<<`  | `a << b`         |
| `>>`  | `a >> b`         |
| `+`   | `a + b`          |
| `-`   | `a - b`          |
| `~`   | `a ~ b`          |
| `*`   | `a * b`          |
| `/`   | `a / b`          |
| `%`   | `a % b`          |

一元运算符：

| 符号    | 案例               |
|-------|------------------|
| `+`   | `+10`            |
| `-`   | `-10`            |
| `!`   | `!a`             |
| `++`  | `++ a` 或者 `a ++` |
| `--`  | `-- a` 或者 `a --` |

## 三元运算

```
5 > 2 ? 10 : 12
```

运算符优先级和 java 一致，上述表达式执行结果为 10；

## null 合并运算

为了方便地在表达式里进行 `null` 判断和转换，表达式引入了类似 `es6` 的 `null` 合并运算符 `??`;

```
a ?? 3
```

上述表达式当变量 `a` 为 `null` 的时候，运算结果为 3；否则为变量 `a` 的值；`null` 合并运算符是可以连续的；

```
a ?? b ?? c ?? d 
```
上述表达式计算结果为 `a`、`b`、`c`、`d` 四个变量中第一个不为 `null`的值；

`??`运算符优先级大于逻辑且 `&&` 和逻辑或 `||`，但小于其它运算符； 这意味着如果运算符与其他运算符同时出现在表达式里，需要注意使用括号；

```
a ?? b > c 
```
如果 `a` 为 `null`，该表达式计算结果为 `b > c`的结果。

