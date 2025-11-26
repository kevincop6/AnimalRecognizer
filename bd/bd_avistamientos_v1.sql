-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Servidor: 127.0.0.1
-- Tiempo de generación: 28-06-2025 a las 20:56:54
-- Versión del servidor: 10.4.32-MariaDB
-- Versión de PHP: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de datos: `bd_avistamientos`
--

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `animales`
--

CREATE TABLE `animales` (
  `id` int(60) NOT NULL,
  `taxonomia` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`taxonomia`)),
  `distribucion` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`distribucion`)),
  `descripcion` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `imagenes` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`imagenes`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `aportes`
--

CREATE TABLE `aportes` (
  `id` int(60) NOT NULL,
  `usuario_id` int(60) NOT NULL,
  `animal_id` int(60) NOT NULL,
  `imagenes` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`imagenes`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `photo_profile`
--

CREATE TABLE `photo_profile` (
  `id` int(80) NOT NULL,
  `user_id` int(60) NOT NULL,
  `photo` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `usuarios`
--

CREATE TABLE `usuarios` (
  `id` int(60) NOT NULL,
  `nombre` varchar(100) NOT NULL,
  `nombre_usuario` text NOT NULL,
  `correo` varchar(255) NOT NULL,
  `contraseña` text NOT NULL,
  `bio` text NOT NULL,
  `fecha_registro` timestamp NOT NULL DEFAULT current_timestamp(),
  `tipo` int(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Índices para tablas volcadas
--

--
-- Indices de la tabla `animales`
--
ALTER TABLE `animales`
  ADD PRIMARY KEY (`id`);

--
-- Indices de la tabla `aportes`
--
ALTER TABLE `aportes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `aportes_ibfk_1` (`animal_id`),
  ADD KEY `usuario_id` (`usuario_id`);

--
-- Indices de la tabla `photo_profile`
--
ALTER TABLE `photo_profile`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`);

--
-- Indices de la tabla `usuarios`
--
ALTER TABLE `usuarios`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `correo` (`correo`);

--
-- AUTO_INCREMENT de las tablas volcadas
--

--
-- AUTO_INCREMENT de la tabla `animales`
--
ALTER TABLE `animales`
  MODIFY `id` int(60) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `aportes`
--
ALTER TABLE `aportes`
  MODIFY `id` int(60) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `photo_profile`
--
ALTER TABLE `photo_profile`
  MODIFY `id` int(80) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `usuarios`
--
ALTER TABLE `usuarios`
  MODIFY `id` int(60) NOT NULL AUTO_INCREMENT;

--
-- Restricciones para tablas volcadas
--

--
-- Filtros para la tabla `aportes`
--
ALTER TABLE `aportes`
  ADD CONSTRAINT `aportes_ibfk_1` FOREIGN KEY (`animal_id`) REFERENCES `animales` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE,
  ADD CONSTRAINT `aportes_ibfk_2` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE;

--
-- Filtros para la tabla `photo_profile`
--
ALTER TABLE `photo_profile`
  ADD CONSTRAINT `photo_profile_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
