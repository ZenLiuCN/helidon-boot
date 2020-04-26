# Helidon SE bootstrap

[![](https://jitpack.io/v/ZenLiuCN/helidon-boot.svg)](https://jitpack.io/#ZenLiuCN/helidon-boot)

 This is bootstrap modules for use [helidon](https://helidon.io/) as application server framework.

## languages
+ `java`

 All artifacts with suffix `-java` is use for development with `java`;

+ `kotlin`

 All artifacts without suffix is develop with jetbrains `kotlin` language;
 
## modules

+ bootstrap
    + for easy configure and start a helidon se application as `Boot`.
    + some util tools for Config as `ConfigUtil`.
    + an abstraction of `plugin` which can extend helidon se with other power tools.
    
+ hikari
    + implement of `plugin` with [hikariCP](https://github.com/brettwooldridge/HikariCP) datasource and connection pool.

