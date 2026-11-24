package kr.ac.hanseo.lostfound.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.common.auth.Role;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.user.AdminRepository;
import kr.ac.hanseo.lostfound.user.Student;
import kr.ac.hanseo.lostfound.user.StudentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String signup(SignupRequest request) {
        if (studentRepository.existsById(request.studentId())) {
            throw new BusinessException(ErrorCode.STUDENT_ID_DUPLICATE);
        }
        Student student = new Student(request.studentId(), passwordEncoder.encode(request.password()),
                request.name().trim(), request.department().trim(), request.phone());
        try {
            studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            // 같은 학번으로 동시에 가입한 경우
            throw new BusinessException(ErrorCode.STUDENT_ID_DUPLICATE);
        }
        return student.getStudentId();
    }

    /** ID·비밀번호 중 어느 쪽이 틀렸는지 알려주지 않는다 */
    @Transactional(readOnly = true)
    public SessionUser authenticate(LoginRequest request) {
        if (request.role() == Role.STUDENT) {
            return studentRepository.findById(request.loginId())
                    .filter(s -> passwordEncoder.matches(request.password(), s.getPasswordHash()))
                    .map(s -> new SessionUser(s.getStudentId(), s.getName(), Role.STUDENT, s.getDepartment()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        }
        return adminRepository.findById(request.loginId())
                .filter(a -> passwordEncoder.matches(request.password(), a.getPasswordHash()))
                .map(a -> new SessionUser(a.getAdminId(), a.getName(), Role.ADMIN, a.getDepartment()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
    }
}
