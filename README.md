<img align="left" src="http://www.vibur.org/img/vibur-130x130.png" alt="Vibur logo"> 
Vibur Object Pool is a general-purpose concurrent Java object pool that is built entirely using standard 
Java concurrency utilities, does not use any synchronized blocks or methods, and does not have any 
external dependencies.

The project [home page](http://www.vibur.org/vibur-object-pool/) contains details of its inner workings,
usage examples, and more.

This project is a main building block of [Vibur DBCP](https://github.com/vibur/vibur-dbcp) - a concurrent 
and dynamic JDBC connection pool. 

The project maven coordinates are:

```
<dependency>
  <groupId>org.vibur</groupId>
  <artifactId>vibur-object-pool</artifactId>
  <version>20.0</version>
</dependency>   
```

[Originally released](https://raw.githubusercontent.com/vibur/vibur-object-pool/master/CHANGELOG) in January 2013 
on code.google.com, the project was migrated to GitHub in March 2015.
