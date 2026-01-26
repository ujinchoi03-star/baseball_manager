-- 원격 접속 가능한 사용자 생성
CREATE USER IF NOT EXISTS 'baseball_user'@'%' IDENTIFIED BY 'ChldnwlswkdwJddn1';

-- 권한 부여
GRANT ALL PRIVILEGES ON baseball_db.* TO 'baseball_user'@'%';

-- 권한 적용
FLUSH PRIVILEGES;